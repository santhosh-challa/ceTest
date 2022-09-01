package com.tmobile.cloud.gcprules.cloudsql;

import com.amazonaws.util.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tmobile.cloud.awsrules.utils.PacmanUtils;
import com.tmobile.cloud.constants.PacmanRuleConstants;
import com.tmobile.cloud.gcprules.utils.GCPUtils;
import com.tmobile.pacman.commons.PacmanSdkConstants;
import com.tmobile.pacman.commons.exception.InvalidInputException;
import com.tmobile.pacman.commons.exception.RuleExecutionFailedExeption;
import com.tmobile.pacman.commons.rule.Annotation;
import com.tmobile.pacman.commons.rule.BaseRule;
import com.tmobile.pacman.commons.rule.PacmanRule;
import com.tmobile.pacman.commons.rule.RuleResult;
import com.tmobile.pacman.commons.utils.CommonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.*;

@PacmanRule(key = "enforce-ssl-to-cloud-sql", desc = "enforce-ssl-cloud-sql", severity = PacmanSdkConstants.SEV_MEDIUM, category = PacmanSdkConstants.SECURITY)
public class EnforceSSLToCloudSQL extends BaseRule {
    private static final Logger logger = LoggerFactory.getLogger(EnforceSSLToCloudSQL.class);

    @Override
    public RuleResult execute(Map<String, String> ruleParam, Map<String, String> resourceAttributes) {

        logger.debug("========Enforce SSL connection for Cloud SQL for =========");
        Annotation annotation = null;

        String resourceId = ruleParam.get(PacmanRuleConstants.RESOURCE_ID);
        String severity = ruleParam.get(PacmanRuleConstants.SEVERITY);
        String category = ruleParam.get(PacmanRuleConstants.CATEGORY);
        String vmEsURL = CommonUtils.getEnvVariableValue(PacmanSdkConstants.ES_URI_ENV_VAR_NAME);

        if (Boolean.FALSE.equals(PacmanUtils.doesAllHaveValue(severity, category, vmEsURL))) {
            logger.info(PacmanRuleConstants.MISSING_CONFIGURATION);
            throw new InvalidInputException(PacmanRuleConstants.MISSING_CONFIGURATION);
        }

        if (!StringUtils.isNullOrEmpty(vmEsURL)) {
            vmEsURL = vmEsURL + "/gcp_cloudsql/_search";

        }
        logger.debug("========vmEsURL URL after concatenation param {}  =========", vmEsURL);

        boolean isSSLEnabled= false;

        MDC.put("executionId", ruleParam.get("executionId"));
        MDC.put("ruleId", ruleParam.get(PacmanSdkConstants.RULE_ID));

        if (!StringUtils.isNullOrEmpty(resourceId)) {

            Map<String, Object> mustFilter = new HashMap<>();
            mustFilter.put(PacmanUtils.convertAttributetoKeyword(PacmanRuleConstants.RESOURCE_ID), resourceId);
            mustFilter.put(PacmanRuleConstants.LATEST, true);

            try {
                isSSLEnabled = isSSLEnforced(vmEsURL, mustFilter);
                if (!isSSLEnabled) {
                    List<LinkedHashMap<String, Object>> issueList = new ArrayList<>();
                    LinkedHashMap<String, Object> issue = new LinkedHashMap<>();

                    annotation = Annotation.buildAnnotation(ruleParam, Annotation.Type.ISSUE);
                    annotation.put(PacmanSdkConstants.DESCRIPTION, "Enforce all incoming connections to your Cloud SQL database instances to use SSL only. If the SSL protocol is not enforced for all Cloud SQL connections, clients without a valid certificate are allowed to connect to the database.");
                    annotation.put(PacmanRuleConstants.SEVERITY, severity);
                    annotation.put(PacmanRuleConstants.CATEGORY, category);
                    issue.put(PacmanRuleConstants.VIOLATION_REASON, "SSL was not enforced for Cloud sql which result in MITM attacks. ");
                    issueList.add(issue);
                    annotation.put("issueDetails", issueList.toString());
                    logger.debug("========SSL was not enforced for Cloud sql which result in MITM attacks and  Rule  ended with an annotation {} : =========", annotation);
                    return new RuleResult(PacmanSdkConstants.STATUS_FAILURE, PacmanRuleConstants.FAILURE_MESSAGE, annotation);
                }

            } catch (Exception exception) {
                throw new RuleExecutionFailedExeption(exception.getMessage());
            }
        }
        logger.debug("========SSL was  enforced for Cloud sql =========");
        return new RuleResult(PacmanSdkConstants.STATUS_SUCCESS, PacmanRuleConstants.SUCCESS_MESSAGE);


    }
    private boolean isSSLEnforced(String vmEsURL, Map<String, Object> mustFilter) throws Exception {
        logger.debug("======== isSSLEnforced started=========");
        JsonArray hitsJsonArray = GCPUtils.getHitsArrayFromEs(vmEsURL, mustFilter);
        boolean validationResult = false;
        if (hitsJsonArray.size() > 0) {
            JsonObject dbinstances = (JsonObject) ((JsonObject) hitsJsonArray.get(0))
                    .get(PacmanRuleConstants.SOURCE);

            logger.debug("Validating the data item: {} and size(){}", dbinstances,hitsJsonArray.size());
            JsonObject settings = dbinstances.getAsJsonObject()
                    .get(PacmanRuleConstants.SETTINGS).getAsJsonObject();
            logger.debug("settings   --> {}", settings);


            if(settings!=null ){
              JsonObject ipConfiguration= settings.get(PacmanRuleConstants.IPCONFIGUATION).getAsJsonObject();

                if(ipConfiguration!=null) {
          if (ipConfiguration.get(PacmanRuleConstants.REQUIRE_SSL) != null) {
                        boolean requireSSL = ipConfiguration.get(PacmanRuleConstants.REQUIRE_SSL).getAsBoolean();
                        if (requireSSL == true) {
                            validationResult = true;
                        }
            }
                }
                else{
                    logger.info(PacmanRuleConstants.RESOURCE_DATA_NOT_FOUND);

                }
            }else {
                logger.info(PacmanRuleConstants.RESOURCE_DATA_NOT_FOUND);
         }

        }  else {
            logger.info(PacmanRuleConstants.RESOURCE_DATA_NOT_FOUND);
        }
        return validationResult;
    }

    @Override
    public String getHelpText() {
        return "check Cloud SQL Enforced to enable SSL connection";
    }
}
