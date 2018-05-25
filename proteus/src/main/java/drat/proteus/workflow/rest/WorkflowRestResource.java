package drat.proteus.workflow.rest;


import java.io.IOException;
import java.util.logging.Logger;



import org.wicketstuff.rest.annotations.MethodMapping;
import org.wicketstuff.rest.annotations.parameters.RequestBody;
import org.wicketstuff.rest.contenthandling.json.webserialdeserial.GsonWebSerialDeserial;
import org.wicketstuff.rest.resource.AbstractRestResource;
import org.wicketstuff.rest.utils.http.HttpMethod;

import backend.FileConstants;

public class WorkflowRestResource extends AbstractRestResource<GsonWebSerialDeserial> {
    
    
    /**
     * 
     */
    private static final long serialVersionUID = -5885885059043262485L;
//    WorkflowManagerUtils wm;
    
    
    
    private static final Logger LOG = Logger.getLogger(WorkflowRestResource.class.getName());
    public WorkflowRestResource() {
        super(new GsonWebSerialDeserial());
//        wm =  new WorkflowManagerUtils(PathUtils.replaceEnvVariables(FileConstants.CLIENT_URL));
    }
    
    @MethodMapping(value = "/dynamic", httpMethod = HttpMethod.POST)
    public String performDynamicWorkFlow(@RequestBody DynamicWorkflowRequestWrapper requestBody ) {
   
//        try {
////            Metadata metaData = new Metadata();
//            LOG.info(requestBody.taskIds.get(0));
////            wm.getClient();
////            wm.getClient().executeDynamicWorkflow(requestBody.taskIds,metaData);
            return "OK";
//        }catch(IOException ex) {
//            LOG.info("Workflow Service Error " + ex.getMessage());
//            return "Connectiing to Server Eroor";
//        }catch(Exception ex) {
//            LOG.info("Workflow Service Error " + ex.getMessage());
//            return "Failed to connect to client Url";
//        }
    }
    
    
}
