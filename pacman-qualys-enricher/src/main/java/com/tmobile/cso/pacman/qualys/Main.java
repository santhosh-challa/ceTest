package com.tmobile.cso.pacman.qualys;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.naming.NamingException;

import com.tmobile.cso.pacman.qualys.jobs.HostAssetDataImporter;
import com.tmobile.cso.pacman.qualys.jobs.KBDataImporter;
import com.tmobile.cso.pacman.qualys.jobs.KernelVersionDataCollector;
import com.tmobile.pacman.commons.jobs.PacmanJob;


/**
 * The Class Main.
 */
@PacmanJob(methodToexecute = "execute", jobName = "Qualys Enricher", desc = "Job to enrich qualys data in ES", priority = 5)
public class Main {

    /**
     * The main method.
     *
     * @param args the arguments
     * @throws Exception the exception
     */
    public static void main(String[] args) throws Exception {
        Map<String, String> params = new HashMap<>();
        Arrays.asList(args).stream().forEach(obj -> {
            for (String param : obj.split("[*]")) {
                String[] paramArray = param.split("=");
                params.put(paramArray[0], paramArray[1]);
            }
        });
        execute(params);
    }

    /**
     * Execute.
     *
     * @param params the params
     * @return 
     * @throws NamingException the naming exception
     */
    public static Map<String, Object> execute(Map<String, String> params) throws NamingException {

        Map<String, Object> errorInfo = new HashMap<>() ;
        String jobHint = params.get("job_hint");
        switch (jobHint) {
        case "qualys":
            errorInfo =  new HostAssetDataImporter().execute();
            break;
        case "qualys-kb":
            errorInfo =  new KBDataImporter().execute();
            break;
        case "qualys-kernel":
            errorInfo =  new KernelVersionDataCollector().execute();
            break;
        
        }
        return  errorInfo;
    }

}
