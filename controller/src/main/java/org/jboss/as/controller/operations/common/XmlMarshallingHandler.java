/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.operations.common;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.logging.ControllerLogger.MGMT_OP_LOGGER;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.SimpleOperationDefinition;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.common.ControllerResolver;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.persistence.ConfigurationPersister;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * A {@link org.jboss.as.controller.OperationStepHandler} that can output a model in XML form
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class XmlMarshallingHandler implements OperationStepHandler{

    private static final String OPERATION_NAME = ModelDescriptionConstants.READ_CONFIG_AS_XML_OPERATION;

    public static final SimpleOperationDefinition DEFINITION = new SimpleOperationDefinitionBuilder(OPERATION_NAME,ControllerResolver.getResolver())
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.READ_WHOLE_CONFIG)
            .setReplyType(ModelType.STRING)
            .setReadOnly()
            .setRuntimeOnly()
            .build();

    private static final Set<Action.ActionEffect> EFFECTS =
            Collections.unmodifiableSet(EnumSet.of(Action.ActionEffect.ADDRESS, Action.ActionEffect.READ_CONFIG));

    private final ConfigurationPersister configPersister;

    public XmlMarshallingHandler(final ConfigurationPersister configPersister) {
        this.configPersister  = configPersister;
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) {
        final PathAddress pa = context.getCurrentAddress();

        AuthorizationResult authResult = context.authorize(operation, EFFECTS);
        if (authResult.getDecision() != AuthorizationResult.Decision.PERMIT) {
            throw ControllerLogger.ROOT_LOGGER.unauthorized(operation.require(OP).asString(), pa, authResult.getExplanation());
        }

        final Resource resource = context.readResourceFromRoot(getBaseAddress());
        // Get the model recursively
        final ModelNode model = Resource.Tools.readModel(resource);
        try {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try {
                BufferedOutputStream output = new BufferedOutputStream(baos);
                configPersister.marshallAsXml(model, output);
                output.close();
                baos.close();
            } finally {
                safeClose(baos);
            }
            String xml = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            context.getResult().set(xml);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // Log this
            MGMT_OP_LOGGER.failedExecutingOperation(e, operation.require(ModelDescriptionConstants.OP), pa);
            context.getFailureDescription().set(e.toString());
        }
    }

    protected PathAddress getBaseAddress() {
        return PathAddress.EMPTY_ADDRESS;
    }

    private void safeClose(final Closeable closeable) {
        if (closeable != null) try {
            closeable.close();
        } catch (Throwable t) {
            MGMT_OP_LOGGER.failedToCloseResource(t, closeable);
        }
    }
}
