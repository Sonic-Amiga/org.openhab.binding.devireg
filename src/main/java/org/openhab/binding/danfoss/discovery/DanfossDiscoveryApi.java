package org.openhab.binding.danfoss.discovery;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.openhab.binding.danfoss.internal.DanfossBindingConstants;
import org.openhab.core.io.rest.JSONResponse;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Component(service = RESTResource.class)
@JaxrsResource
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@Path("/" + DanfossBindingConstants.BINDING_ID)
@Tag(name = DanfossBindingConstants.BINDING_ID)
@Produces(MediaType.APPLICATION_JSON)
public class DanfossDiscoveryApi implements RESTResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/receive/{otp: [0-9]+}")
    @Operation(summary = "Receive Danfoss configuration", responses = {
            @ApiResponse(responseCode = "200", description = "OK"),
            @ApiResponse(responseCode = "400", description = "Invalid OTP supplied"),
            @ApiResponse(responseCode = "404", description = "Connection refused, likely wrong OTP"),
            @ApiResponse(responseCode = "500", description = "Communication error"),
            @ApiResponse(responseCode = "503", description = "Discovery service shut down") })
    public Response receiveConfig(@PathParam("otp") @Parameter(description = "One-time password") String otp) {
        DanfossDiscoveryService discovery = DanfossDiscoveryService.get();

        if (discovery != null) {
            return discovery.receiveConfig(otp);
        } else {
            return JSONResponse.createErrorResponse(Status.SERVICE_UNAVAILABLE,
                    "Danfoss discovery service is not running");
        }
    }
}
