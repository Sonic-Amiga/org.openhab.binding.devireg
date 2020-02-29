/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.devireg.internal;

import static org.openhab.binding.devireg.internal.DeviRegBindingConstants.THING_TYPE_DEVIREG_SMART;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandlerFactory;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerFactory;
import org.openhab.binding.devireg.discovery.DeviRegDiscoveryServlet;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.HttpService;

/**
 * The {@link DeviRegHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Pavel Fedin - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.devireg", service = ThingHandlerFactory.class)
public class DeviRegHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections
            .unmodifiableSet(Stream.of(THING_TYPE_DEVIREG_SMART).collect(Collectors.toSet()));

    @Reference
    @NonNullByDefault({})
    ConfigurationAdmin configurationAdmin;

    @Nullable
    private HttpService httpService;

    @Nullable
    private DeviRegDiscoveryServlet servlet;

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    // The activate component call is used to access the bindings configuration
    @Activate
    protected void activate(ComponentContext componentContext, Map<String, Object> config) {
        super.activate(componentContext);
        DeviRegBindingConfig.update(config, configurationAdmin);
        if (servlet == null) {
            servlet = new DeviRegDiscoveryServlet(httpService);
        }

    }

    @Override
    @Deactivate
    protected void deactivate(ComponentContext componentContext) {
        if (servlet != null) {
            servlet.dispose();
            servlet = null;
        }
        super.deactivate(componentContext);
    }

    @Modified
    protected void modified(Map<String, Object> config) {
        // We update instead of replace the configuration object, so that if the user updates the
        // configuration, the values are automatically available in all handlers. Because they all
        // share the same instance.
        DeviRegBindingConfig.update(config, configurationAdmin);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_DEVIREG_SMART.equals(thingTypeUID)) {
            return new DeviRegHandler(thing);
        }

        return null;
    }

    @Reference(cardinality = ReferenceCardinality.MANDATORY, policy = ReferencePolicy.DYNAMIC)
    protected void setHttpService(HttpService svc) {
        httpService = svc;
    }

    protected void unsetHttpService(HttpService svc) {
        httpService = null;
    }
}
