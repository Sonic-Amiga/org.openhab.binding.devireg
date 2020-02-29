package org.openhab.binding.devireg.discovery;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jdt.annotation.Nullable;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DeviRegDiscoveryServlet extends HttpServlet {

    private static final long serialVersionUID = 4277870755120852304L;
    private final Logger logger = LoggerFactory.getLogger(DeviRegDiscoveryServlet.class);
    private static final String RESOURCE_URL = "/devireg";

    private HttpService httpService;

    public DeviRegDiscoveryServlet(HttpService svc) {
        httpService = svc;
        try {
            httpService.registerServlet(RESOURCE_URL, this, null, httpService.createDefaultHttpContext());
        } catch (NamespaceException | ServletException e) {
            logger.error("Register servlet fails", e);
        }
    }

    public void dispose() {
        httpService.unregister(RESOURCE_URL);
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws ServletException, IOException {

        if (req == null || resp == null) {
            return;
        }

        String filePath = req.getRequestURI().substring(RESOURCE_URL.length());
        logger.trace("GET " + filePath);

        if (filePath.isEmpty()) {
            resp.sendRedirect(RESOURCE_URL + "/");
            return;
        } else if (filePath.equals("/")) {
            filePath = "/configreceiver.html";
        }

        InputStream in = getClass().getResourceAsStream("/ui" + filePath);

        if (in != null) {
            String type;

            // guessContentTypeFromName() doesn't know .js
            if (filePath.endsWith(".js")) {
                type = "application/javascript";
            } else {
                type = URLConnection.guessContentTypeFromName(filePath);
            }

            logger.trace("content-type " + type);

            resp.addHeader("content-type", type);
            IOUtils.copy(in, resp.getOutputStream());
        } else {
            resp.sendError(404, "Not found");
        }
    }
}
