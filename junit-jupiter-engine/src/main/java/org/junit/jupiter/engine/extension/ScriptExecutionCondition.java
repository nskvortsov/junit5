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
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ScriptEvaluationException;
import org.junit.jupiter.engine.script.Script;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;

/**
 * {@link ExecutionCondition} that supports the {@link DisabledIf} and {@link EnabledIf} annotation.
 *
 * @since 5.1
 * @see DisabledIf
 * @see EnabledIf
 * @see #evaluateExecutionCondition(ExtensionContext)
 */
class ScriptExecutionCondition implements ExecutionCondition {

	private static final Logger logger = LoggerFactory.getLogger(ScriptExecutionCondition.class);

	private static final ConditionEvaluationResult ENABLED_NO_ELEMENT = enabled("AnnotatedElement not present");

	private static final ConditionEvaluationResult ENABLED_NO_ANNOTATION = enabled("Annotation not present");

	private static final Namespace NAMESPACE = Namespace.create(ScriptExecutionCondition.class);

	private static final String NAME = "org.junit.jupiter.engine.extension.ScriptExecutionWorker";

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

		Worker worker = getScriptExecutionWorker(context);
		return worker.work(context, scripts);
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

	private Worker getScriptExecutionWorker(ExtensionContext context) {
		ExtensionContext.Store rootStore = context.getRoot().getStore(NAMESPACE);
		return rootStore.getOrComputeIfAbsent(NAME, this::createWorker, Worker.class);
	}

	// Create worker via reflection to hide the `javax.script` dependency.
	private Worker createWorker(String name) {
		try {
			logger.debug(() -> "Creating instance of " + name);
			return (Worker) Class.forName(name).getDeclaredConstructor().newInstance();
		}
		catch (ReflectiveOperationException e) {
			logger.error(e, () -> String.format("Creating `%s` failed", name));
			return new ThrowingWorker(e);
		}
	}

	/**
	 * Evaluates scripts and returns a conditional evaluation result.
	 */
	interface Worker {
		ConditionEvaluationResult work(ExtensionContext context, List<Script> scripts);
	}

	/**
	 * Worker implementation that always throws an {@link ScriptEvaluationException}.
	 */
	static class ThrowingWorker implements Worker {

		final ScriptEvaluationException exception;

		ThrowingWorker(Throwable cause) {
			String message = "ScriptExecutionCondition extension is in an illegal state, " //
					+ "script evaluation is disabled. " //
					+ "If the originating cause is a `NoClassDefFoundError: javax/script/...` and " //
					+ "the underlying runtime environment is executed with an activated module system " //
					+ "(aka Jigsaw or JPMS) you need to add the `java.scripting` module to the " //
					+ "root modules via `--add-modules ...,java.scripting`";

			this.exception = new ScriptEvaluationException(message, cause);
		}

		@Override
		public ConditionEvaluationResult work(ExtensionContext context, List<Script> scripts) {
			throw exception;
		}
	}
}
