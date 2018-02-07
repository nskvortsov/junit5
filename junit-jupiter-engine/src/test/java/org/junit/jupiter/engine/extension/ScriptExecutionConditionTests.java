/*
 * Copyright 2015-2018 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v2.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v20.html
 */

package org.junit.jupiter.engine.extension;

import static org.assertj.core.api.Assertions.allOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.test.event.ExecutionEventConditions.assertRecordedExecutionEventsContainsExactly;
import static org.junit.platform.engine.test.event.ExecutionEventConditions.event;
import static org.junit.platform.engine.test.event.ExecutionEventConditions.finishedWithFailure;
import static org.junit.platform.engine.test.event.ExecutionEventConditions.test;
import static org.junit.platform.engine.test.event.TestExecutionResultConditions.isA;
import static org.junit.platform.engine.test.event.TestExecutionResultConditions.message;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

import java.lang.reflect.AnnotatedElement;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ScriptEvaluationException;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.jupiter.engine.TrackLogRecords;
import org.junit.platform.commons.JUnitException;
import org.junit.platform.engine.test.event.ExecutionEventRecorder;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.mockito.Mockito;

/**
 * Unit tests for {@link ScriptExecutionCondition}.
 *
 * @since 5.1
 */
class ScriptExecutionConditionTests extends AbstractJupiterTestEngineTests {

	@Test
	void executeSimpleTestCases() {
		LauncherDiscoveryRequest request = request().selectors(selectClass(SimpleTestCases.class)).build();
		ExecutionEventRecorder eventRecorder = executeTests(request);

		assertAll("Summary of simple test cases run", //
			() -> assertEquals(3, eventRecorder.getTestStartedCount(), "# tests started"), //
			() -> assertEquals(1, eventRecorder.getTestSkippedCount(), "# tests skipped"), //
			() -> assertEquals(1, eventRecorder.getTestFailedCount(), "# tests started") //

		);

		assertRecordedExecutionEventsContainsExactly(eventRecorder.getFailedTestFinishedEvents(), //
			event(test("syntaxError"), //
				finishedWithFailure( //
					allOf( //
						isA(JUnitException.class), //
						message(value -> value.contains("syntax error")) //
					))));

	}

	@Test
	@EnabledIf("true")
	@DisabledIf("false")
	void annotationDefaultValues(TestInfo info) {
		EnabledIf e = info.getTestMethod().orElseThrow(Error::new).getDeclaredAnnotation(EnabledIf.class);
		assertEquals("Nashorn", e.engine());
		assertEquals("Script `{source}` evaluated to: {result}", e.reason());
		DisabledIf d = info.getTestMethod().orElseThrow(Error::new).getDeclaredAnnotation(DisabledIf.class);
		assertEquals("Nashorn", d.engine());
		assertEquals("Script `{source}` evaluated to: {result}", d.reason());
	}

	@Test
	void throwingEvaluatorExceptionMessage() {
		ReflectiveOperationException cause = new ReflectiveOperationException("Mock for ReflectiveOperationException");
		ScriptExecutionCondition.Evaluator evaluator = new ScriptExecutionCondition.ThrowingEvaluator(cause);
		Exception e = assertThrows(ScriptEvaluationException.class, () -> evaluator.evaluate(null, null));
		assertThat(e.getMessage()).contains("ScriptExecutionCondition", "illegal state", "NoClassDefFoundError",
			"--add-modules", "java.scripting");
	}

	@Test
	void enabledDueToAnnotatedElementNotPresent() {
		ScriptExecutionCondition condition = new ScriptExecutionCondition();
		ExtensionContext context = Mockito.mock(ExtensionContext.class);
		ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);
		assertFalse(result.isDisabled());
		assertTrue(result.getReason().isPresent());
		result.getReason().ifPresent(reason -> assertEquals("AnnotatedElement not present", reason));
	}

	@Test
	void enabledDueToAnnotationNotPresent() {
		ScriptExecutionCondition condition = new ScriptExecutionCondition();
		ExtensionContext context = Mockito.mock(ExtensionContext.class);
		Optional<AnnotatedElement> optionalElement = Optional.of(ScriptExecutionConditionTests.class);
		Mockito.when(context.getElement()).thenReturn(optionalElement);
		ConditionEvaluationResult result = condition.evaluateExecutionCondition(context);
		assertFalse(result.isDisabled());
		assertTrue(result.getReason().isPresent());
		result.getReason().ifPresent(reason -> assertEquals("Annotation not present", reason));
	}

	@Test
	@TrackLogRecords
	void throwingEvaluatorIsCreatedWhenScriptEngineIsNotAvailable() throws ReflectiveOperationException {
		ScriptExecutionCondition condition = new ScriptExecutionCondition("illegal class name");
		ExtensionContext context = Mockito.mock(ExtensionContext.class);
		AnnotatedElement element = SimpleTestCases.class.getDeclaredMethod("testIsEnabled");
		Mockito.when(context.getElement()).thenReturn(Optional.of(element));
		Exception e = assertThrows(ScriptEvaluationException.class,
			() -> condition.evaluateExecutionCondition(context));
		assertThat(e.getMessage()).contains("ScriptExecutionCondition", "illegal state", "NoClassDefFoundError",
			"--add-modules", "java.scripting");
	}

	static class SimpleTestCases {

		@Test
		@EnabledIf("true")
		void testIsEnabled() {
		}

		@Test
		@DisabledIf("false")
		void testIsNotDisabled() {
		}

		@Test
		@EnabledIf("syntax error")
		void syntaxErrorFails() {
			fail("test must not be executed");
		}

		@Test
		@DisabledIf("junitConfigurationParameter.get('does-not-exist') == null")
		void accessingNonExistentJUnitConfigurationParameter() {
			fail("test must not be executed");
		}
	}

}
