package org.openmetadata.service.util;

import org.openmetadata.schema.entity.services.MetadataService;

import java.util.LinkedHashMap;
import java.util.Locale;

public class OpenMetadataServiceUtil {
    public static MetadataService removeESInfo(MetadataService metadataService){
        if(metadataService.getFullyQualifiedName().toUpperCase(Locale.ROOT).equals("OPENMETADATA")){
            try{
                LinkedHashMap<String, Object> configConnection = (LinkedHashMap<String, Object>) metadataService.getConnection().getConfig();
                LinkedHashMap<String, Object> elasticConfig = (LinkedHashMap<String, Object>) configConnection.get("elasticsSearch");
                LinkedHashMap<String, Object> valueElasticConfig = (LinkedHashMap<String, Object>) elasticConfig.get("config");
                valueElasticConfig.remove("es_password");
                valueElasticConfig.remove("es_username");
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        return metadataService;
    }
}
