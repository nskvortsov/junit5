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

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;
import static org.junit.platform.commons.util.AnnotationUtils.findAnnotation;

import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ScriptEvaluationException;
import org.junit.jupiter.engine.script.Script;

/**
 * {@link ExecutionCondition} that supports the {@link DisabledIf} and {@link EnabledIf} annotation.
 *
 * @since 5.1
 * @see DisabledIf
 * @see EnabledIf
 * @see #evaluateExecutionCondition(ExtensionContext)
 */
class ScriptExecutionCondition implements ExecutionCondition {

	private static final ConditionEvaluationResult ENABLED_NO_ELEMENT = enabled("AnnotatedElement not present");

	private static final ConditionEvaluationResult ENABLED_NO_ANNOTATION = enabled("Annotation not present");

	private static final String EVALUATOR_CLASS_NAME = "org.junit.jupiter.engine.extension.ScriptExecutionEvaluator";

	private final Evaluator evaluator;

	// Used by the ExtensionRegistry.
	ScriptExecutionCondition() {
		this(EVALUATOR_CLASS_NAME);
	}

	// Used by tests.
	ScriptExecutionCondition(String evaluatorImplementationName) {
		this.evaluator = Evaluator.forName(evaluatorImplementationName);
	}

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		// Context without an annotated element?
		Optional<AnnotatedElement> element = context.getElement();
		if (!element.isPresent()) {
			return ENABLED_NO_ELEMENT;
		}
		AnnotatedElement annotatedElement = element.get();

		// Always try to create script instances.
		List<Script> scripts = new ArrayList<>();
		createDisabledIfScript(annotatedElement).ifPresent(scripts::add);
		createEnabledIfScript(annotatedElement).ifPresent(scripts::add);

		// If no scripts are created, no annotation of interest is attached to the underlying element.
		if (scripts.isEmpty()) {
			return ENABLED_NO_ANNOTATION;
		}

		// Let the evaluator do its work.
		return evaluator.evaluate(context, scripts);
	}

	private Optional<Script> createDisabledIfScript(AnnotatedElement annotatedElement) {
		Optional<DisabledIf> disabled = findAnnotation(annotatedElement, DisabledIf.class);
		if (!disabled.isPresent()) {
			return Optional.empty();
		}
		DisabledIf annotation = disabled.get();
		String source = createSource(annotation.value());
		Script script = new Script(annotation, annotation.engine(), source, annotation.reason());
		return Optional.of(script);
	}

	private Optional<Script> createEnabledIfScript(AnnotatedElement annotatedElement) {
		Optional<EnabledIf> enabled = findAnnotation(annotatedElement, EnabledIf.class);
		if (!enabled.isPresent()) {
			return Optional.empty();
		}
		EnabledIf annotation = enabled.get();
		String source = createSource(annotation.value());
		Script script = new Script(annotation, annotation.engine(), source, annotation.reason());
		return Optional.of(script);
	}

	private String createSource(String[] lines) {
		return String.join(System.lineSeparator(), lines);
	}

	/**
	 * Evaluates scripts and returns a conditional evaluation result.
	 */
	interface Evaluator {

		ConditionEvaluationResult evaluate(ExtensionContext context, List<Script> scripts);

		/**
		 * Create evaluator via reflection to hide the `javax.script` dependency.
		 */
		static Evaluator forName(String name) {
			Optional<Throwable> optionalThrowable = assumeScriptEngineIsLoadableByClassForName();
			if (optionalThrowable.isPresent()) {
				return new ThrowingEvaluator(optionalThrowable.get());
			}
			try {
				return (Evaluator) Class.forName(name).getDeclaredConstructor().newInstance();
			}
			catch (ReflectiveOperationException cause) {
				return new ThrowingEvaluator(cause);
			}
		}

		/**
		 * This method may fail on JREs that don't provide the "javax.script" package at all
		 * or it fails on JREs launched with an active module system using insufficient module
		 * graphs, i.e. the application does not read "java.scripting" module.
		 */
		static Optional<Throwable> assumeScriptEngineIsLoadableByClassForName() {
			try {
				Class.forName("javax.script.ScriptEngine");
				return Optional.empty();
			}
			catch (Throwable cause) {
				return Optional.of(cause);
			}
		}
	}

	/**
	 * Evaluator implementation that always throws an {@link ScriptEvaluationException}.
	 */
	static class ThrowingEvaluator implements Evaluator {

		final ScriptEvaluationException exception;

		ThrowingEvaluator(Throwable cause) {
			String message = "ScriptExecutionCondition extension is in an illegal state, " //
					+ "script evaluation is disabled. " //
					+ "If the originating cause is a `NoClassDefFoundError: javax/script/...` and " //
					+ "the underlying runtime environment is executed with an activated module system " //
					+ "(aka Jigsaw or JPMS) you need to add the `java.scripting` module to the " //
					+ "root modules via `--add-modules ...,java.scripting`";

			this.exception = new ScriptEvaluationException(message, cause);
		}

		@Override
		public ConditionEvaluationResult evaluate(ExtensionContext context, List<Script> scripts) {
			throw exception;
		}
	}

}
