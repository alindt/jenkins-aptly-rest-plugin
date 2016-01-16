package org.jenkinsci.plugins.aptly;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import org.json.JSONObject;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.GetRequest;
import com.mashape.unirest.request.HttpRequestWithBody;
import com.mashape.unirest.request.body.MultipartBody;

import org.apache.commons.lang.StringUtils;
import java.io.PrintStream;
import java.io.IOException;
import java.io.Console;
import java.util.List;
import java.io.File;
import java.util.UUID;





/**
 * This class implements a subset of the Aptly REST client calls
 * as documented at
 * http://www.aptly.info/doc/api/
 *
 * @author $Author: zgyarmati <mr.zoltan.gyarmati@gmail.com>
 */
public class AptlyRestClient {


    private String hostname;
    private int portnum;
    private int timeout;
    private String username;
    private String password;

    public AptlyRestClient(String hostname, int portnum, int timeout,
                          String username, String password ){
        this.hostname = hostname;
        this.portnum  = portnum;
        this.timeout  = timeout;
        this.username = username;
        this.password = password;
    }

    public String getAptlyServerVersion() throws AptlyRestException {
        String retval = "";
        try {
            // http://localhost:1080/api/version
            GetRequest req = Unirest.get("http://" + hostname + ":" + portnum +
                                         "/api/version");
            req = req.header("accept", "application/json");
            HttpResponse<JsonNode> jsonResponse = req.asJson();

            System.console().printf("Response: " + jsonResponse.getBody().toString() + "\n");
            retval = jsonResponse.getBody().getObject().getString("Version");
            System.console().printf("Version: " + retval + "\n");

        } catch (UnirestException ex) {
            System.console().printf("Failed to get version: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
        return retval;
    }

    public String uploadFiles(List<File> filepaths) throws AptlyRestException {
        //used to distinguish the upload dir on the aptly server
        String uuid = UUID.randomUUID().toString();
        System.out.println("upload dir name UUID = " + uuid);
        try {
            HttpRequestWithBody req = Unirest.post("http://" + hostname + ":" + portnum +
                                         "/api/files/" + uuid);
            req = req.header("accept", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }
            HttpResponse<JsonNode> jsonResponse = req.field("file", filepaths).asJson();
            System.console().printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
        } catch (UnirestException ex) {
            System.console().printf("Failed to upload the packages: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
        return uuid;
    }

    public void addUploadedFilesToRepo(String reponame, String uploaddir) throws AptlyRestException {
        // add to the repo
        try {
            HttpRequestWithBody req = Unirest.post("http://" + hostname + ":" + portnum +
                                            "/api/repos/"+ reponame +"/file/" + uploaddir);
            req = req.header("accept", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }

            HttpResponse<JsonNode> jsonResponse = req.asJson();
            System.console().printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
        } catch (UnirestException ex) {
            System.console().printf("Failed to add uploaded packages to repo: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
    }

    // update published repo
    public void updatePublishRepo(String distribution) throws AptlyRestException {
        try {
            HttpRequestWithBody req = Unirest.put("http://" + hostname + ":" + portnum +
                                         "/api/publish//" + distribution);
            req = req.header("accept", "application/json");
            req = req.header("Content-Type", "application/json");
            if( username != null && !username.isEmpty()){
                req = req.basicAuth(username, password);
            }
            HttpResponse<JsonNode> jsonResponse = req.body("{\"Signing\":{\"Skip\": true }}").asJson();
            //HttpResponse<JsonNode> jsonResponse = req.field("Snapshots", "").asJson();
            System.console().printf("Response code: <%d>, body <%s>\n",
                    jsonResponse.getStatus(), jsonResponse.getBody().toString());
        } catch (UnirestException ex) {
            System.console().printf("Failed to publish repo: %s\n", ex.toString());
            throw new AptlyRestException(ex.toString());
        }
    }


    /**
     * @return the portnum
     */
    public  int getPortnum() {
        return portnum;
    }

    /**
     * @param aPortnum the portnum to set
     */
    public void setPortnum(int aPortnum) {
        portnum = aPortnum;
    }

    /**
     * @return the timeout
     */
    public int getTimeout() {
        return timeout;
    }

    /**
     * @param aTimeout the timeout to set
     */
    public void setTimeout(int aTimeout) {
        timeout = aTimeout;
        Unirest.setTimeouts(timeout * 1000, timeout * 1000);
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
