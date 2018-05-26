package drat.proteus.workflow.rest;


import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

import org.apache.oodt.cas.metadata.Metadata;
import org.apache.oodt.cas.metadata.util.PathUtils;
import org.apache.oodt.cas.workflow.system.WorkflowManagerClient;
import org.apache.oodt.cas.workflow.system.rpc.RpcCommunicationFactory;
import org.wicketstuff.rest.annotations.MethodMapping;
import org.wicketstuff.rest.annotations.parameters.RequestBody;
import org.wicketstuff.rest.contenthandling.json.webserialdeserial.GsonWebSerialDeserial;
import org.wicketstuff.rest.resource.AbstractRestResource;
import org.wicketstuff.rest.utils.http.HttpMethod;

import backend.FileConstants;
import drat.proteus.workflow.rest.DynamicWorkflowRequestWrapper;


public class WorkflowRestResource extends AbstractRestResource<GsonWebSerialDeserial> {
    
    
    /**
     * 
     */
    private static final long serialVersionUID = -5885885059043262485L;
    private WorkflowManagerClient wmClient;
    
    
    
    private static final Logger LOG = Logger.getLogger(WorkflowRestResource.class.getName());
    public WorkflowRestResource() {
        super(new GsonWebSerialDeserial());
        try {
        	wmClient = RpcCommunicationFactory.createClient(new URL(PathUtils.replaceEnvVariables(FileConstants.CLIENT_URL)));
        }catch(MalformedURLException ex) {
        	LOG.info("Error creating workflow manager client for url from Environment variable");
        	LOG.severe(ex.getMessage());
        	wmClient = RpcCommunicationFactory.createClient(null);
        }
    }
    
    @MethodMapping(value = "/dynamic", httpMethod = HttpMethod.POST)
    public String performDynamicWorkFlow(@RequestBody DynamicWorkflowRequestWrapper requestBody ) {
   
        try {
            Metadata metaData = new Metadata();
            LOG.info(requestBody.taskIds.get(0));
            
            wmClient.executeDynamicWorkflow(requestBody.taskIds,metaData);
            return "OK";
        }catch(IOException ex) {
            LOG.info("Workflow Service Error " + ex.getMessage());
            return "Connectiing to Server Eroor";
        }catch(Exception ex) {
            LOG.info("Workflow Service Error " + ex.getMessage());
            return "Failed to connect to client Url";
        }
    }
    
    
}
