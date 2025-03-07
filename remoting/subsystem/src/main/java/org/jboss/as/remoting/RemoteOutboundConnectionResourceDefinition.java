/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.AUTHENTICATION_CONTEXT_CAPABILITY;

import java.util.Collection;
import java.util.List;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author Jaikiran Pai
 */
class RemoteOutboundConnectionResourceDefinition extends AbstractOutboundConnectionResourceDefinition {

    static final PathElement PATH = PathElement.pathElement(CommonAttributes.REMOTE_OUTBOUND_CONNECTION);

    static final SimpleAttributeDefinition OUTBOUND_SOCKET_BINDING_REF = new SimpleAttributeDefinitionBuilder(CommonAttributes.OUTBOUND_SOCKET_BINDING_REF, ModelType.STRING, false)
            .setAllowExpression(true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, false, true))
            .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SOCKET_BINDING_REF)
            .setCapabilityReference(OUTBOUND_SOCKET_BINDING_CAPABILITY_NAME, OUTBOUND_CONNECTION_CAPABILITY)
            .build();

    public static final SimpleAttributeDefinition USERNAME = new SimpleAttributeDefinitionBuilder(CommonAttributes.USERNAME, ModelType.STRING, true)
            .setAllowExpression(true)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(RemotingSubsystemModel.VERSION_4_0_0.getVersion())
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, true))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.CREDENTIAL)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .build();

    public static final SimpleAttributeDefinition SECURITY_REALM = new SimpleAttributeDefinitionBuilder(CommonAttributes.SECURITY_REALM, ModelType.STRING, true)
            .setValidator(new StringLengthValidator(1, Integer.MAX_VALUE, true, false))
            .addAccessConstraint(SensitiveTargetAccessConstraintDefinition.SECURITY_REALM_REF)
            .addAccessConstraint(RemotingExtension.REMOTING_SECURITY_DEF)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(RemotingSubsystemModel.VERSION_4_0_0.getVersion())
            .build();

    public static final SimpleAttributeDefinition PROTOCOL = new SimpleAttributeDefinitionBuilder(
            CommonAttributes.PROTOCOL, ModelType.STRING, true).setValidator(EnumValidator.create(Protocol.class))
            .setDefaultValue(new ModelNode(Protocol.HTTP_REMOTING.toString()))
            .setAllowExpression(true)
            .setAlternatives(CommonAttributes.AUTHENTICATION_CONTEXT)
            .setDeprecated(RemotingSubsystemModel.VERSION_4_0_0.getVersion())
            .build();

    static final SimpleAttributeDefinition AUTHENTICATION_CONTEXT = new SimpleAttributeDefinitionBuilder(CommonAttributes.AUTHENTICATION_CONTEXT, ModelType.STRING, true)
            .setCapabilityReference(AUTHENTICATION_CONTEXT_CAPABILITY, OUTBOUND_CONNECTION_CAPABILITY_NAME)
            .setAlternatives(CommonAttributes.USERNAME, CommonAttributes.SECURITY_REALM, CommonAttributes.PROTOCOL)
            .setAccessConstraints(SensitiveTargetAccessConstraintDefinition.AUTHENTICATION_CLIENT_REF)
            .build();

    static final Collection<AttributeDefinition> ATTRIBUTES = List.of(OUTBOUND_SOCKET_BINDING_REF, USERNAME, SECURITY_REALM, PROTOCOL, AUTHENTICATION_CONTEXT);

    RemoteOutboundConnectionResourceDefinition() {
        this(new RemoteOutboundConnectionAdd());
    }

    private RemoteOutboundConnectionResourceDefinition(RemoteOutboundConnectionAdd addHandler) {
        super(new Parameters(PATH, RemotingExtension.getResourceDescriptionResolver(CommonAttributes.REMOTE_OUTBOUND_CONNECTION))
                .setAddHandler(addHandler)
                .setRemoveHandler(new ServiceRemoveStepHandler(OUTBOUND_CONNECTION_CAPABILITY.getCapabilityServiceName(), addHandler))
        );
    }

    @Override
    public void registerChildren(ManagementResourceRegistration resourceRegistration) {
        resourceRegistration.registerSubModel(new PropertyResource(CommonAttributes.REMOTE_OUTBOUND_CONNECTION));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        OperationStepHandler writeHandler = new ReloadRequiredWriteAttributeHandler(ATTRIBUTES);
        for (AttributeDefinition attribute : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(attribute, null, writeHandler);
        }
    }
}
