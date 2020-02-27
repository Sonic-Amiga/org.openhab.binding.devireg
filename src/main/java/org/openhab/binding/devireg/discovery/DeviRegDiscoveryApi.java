package org.openhab.binding.devireg.discovery;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.smarthome.io.rest.JSONResponse;
import org.eclipse.smarthome.io.rest.RESTResource;
import org.osgi.service.component.annotations.Component;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

@Component(service = RESTResource.class)
@Path("/devireg")
@Api(value = "devireg")
@Produces(MediaType.APPLICATION_JSON)
public class DeviRegDiscoveryApi implements RESTResource {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/receive/{otp: [0-9]+}")
    @ApiOperation(value = "Receive DeviSmart configuration")
    @ApiResponses({ @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "Invalid OTP supplied"),
            @ApiResponse(code = 404, message = "Connection refused, likely wrong OTP"),
            @ApiResponse(code = 500, message = "Communication error"),
            @ApiResponse(code = 503, message = "Discovery service shut down") })
    public Response receiveConfig(@PathParam("otp") @ApiParam(value = "One-time password") String otp) {
        DeviRegDiscoveryService discovery = DeviRegDiscoveryService.get();

        if (discovery != null) {
            return discovery.receiveConfig(otp);
        } else {
            return JSONResponse.createErrorResponse(Status.SERVICE_UNAVAILABLE,
                    "DeviReg discovery service is not running");
        }
    }
}
