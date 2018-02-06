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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.stream.Stream;

import javax.script.Bindings;
import javax.script.SimpleBindings;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ScriptEvaluationException;
import org.junit.jupiter.engine.AbstractJupiterTestEngineTests;
import org.junit.jupiter.engine.script.Script;
import org.junit.jupiter.engine.script.ScriptExecutionManager;

/**
 * Unit tests for {@link ScriptExecutionWorker}.
 *
 * @since 5.1
 */
class ScriptExecutionWorkerTests extends AbstractJupiterTestEngineTests {

	private final Bindings bindings = createDefaultContextBindings();
	private final ScriptExecutionManager manager = new ScriptExecutionManager();
	private final ScriptExecutionWorker worker = new ScriptExecutionWorker();

	@Test
	void computeConditionEvaluationResultWithDefaultReasonMessage() {
		Script script = script(EnabledIf.class, "?");
		String actual = worker.computeConditionEvaluationResult(script, "!").getReason().orElseThrow(Error::new);
		assertEquals("Script `?` evaluated to: !", actual);
	}

	@TestFactory
	Stream<DynamicTest> computeConditionEvaluationResultFailsForUnsupportedAnnotationType() {
		return Stream.of(Override.class, Deprecated.class, Object.class) //
				.map(type -> dynamicTest("computationFailsFor(" + type + ")", //
					() -> computeConditionEvaluationResultFailsForUnsupportedAnnotationType(type)));
	}

	private void computeConditionEvaluationResultFailsForUnsupportedAnnotationType(Type type) {
		Script script = new Script(type, "annotation", "engine", "source", "reason");
		Exception e = assertThrows(ScriptEvaluationException.class,
			() -> worker.computeConditionEvaluationResult(script, "!"));
		String expected = "Unsupported annotation type: " + type;
		String actual = e.getMessage();
		assertEquals(expected, actual);
	}

	@Test
	void defaultConditionEvaluationResultProperties() {
		Script script = script(EnabledIf.class, "true");
		ConditionEvaluationResult result = evaluate(script);
		assertFalse(result.isDisabled());
		assertThat(result.toString()).contains("ConditionEvaluationResult", "enabled", "true", "reason");
	}

	@Test
	void getJUnitConfigurationParameterWithJavaScript() {
		Script script = script(EnabledIf.class, "junitConfigurationParameter.get('XXX')");
		Exception exception = assertThrows(ScriptEvaluationException.class, () -> evaluate(script));
		assertThat(exception.getMessage()).contains("Script returned `null`");
	}

	@Test
	void getJUnitConfigurationParameterWithJavaScriptAndCheckForNull() {
		Script script = script(EnabledIf.class, "junitConfigurationParameter.get('XXX') != null");
		ConditionEvaluationResult result = evaluate(script);
		assertTrue(result.isDisabled());
		String actual = result.getReason().orElseThrow(() -> new AssertionError("causeless"));
		assertEquals("Script `junitConfigurationParameter.get('XXX') != null` evaluated to: false", actual);
	}

	private ConditionEvaluationResult evaluate(Script script) {
		return worker.evaluate(manager, script, bindings);
	}

	private Script script(Type type, String... lines) {
		return new Script( //
			type, //
			"Mock for " + type, //
			Script.DEFAULT_SCRIPT_ENGINE_NAME, //
			String.join("\n", lines), //
			Script.DEFAULT_SCRIPT_REASON_PATTERN //
		);
	}

	private Bindings createDefaultContextBindings() {
		Bindings bindings = new SimpleBindings();
		bindings.put(Script.BIND_JUNIT_TAGS, Collections.emptySet());
		bindings.put(Script.BIND_JUNIT_UNIQUE_ID, "Mock for UniqueId");
		bindings.put(Script.BIND_JUNIT_DISPLAY_NAME, "Mock for DisplayName");
		bindings.put(Script.BIND_JUNIT_CONFIGURATION_PARAMETER, Collections.emptyMap());
		return bindings;
	}

}
