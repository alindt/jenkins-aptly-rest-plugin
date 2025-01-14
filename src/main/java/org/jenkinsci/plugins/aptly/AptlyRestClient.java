/*
 * The MIT License
 *
 * Copyright (c) 2017 Zoltan Gyarmati (http://zgyarmati.de)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.jenkins.plugins.aptlyrest;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.PrintStream;
import java.util.List;
import java.io.File;

import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.conn.ssl.*;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;
import kong.unirest.UnirestException;
import kong.unirest.HttpRequest;
import kong.unirest.GetRequest;
import kong.unirest.HttpRequestWithBody;
import kong.unirest.MultipartBody;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This class implements a subset of the Aptly REST client calls
 * as documented at http://www.aptly.info/doc/api/
 * @author $Author: zgyarmati <mr.zoltan.gyarmati@gmail.com>
 */
public class AptlyRestClient {

    private AptlySite   mSite;
    private PrintStream mLogger;

    public AptlyRestClient(PrintStream logger, AptlySite site)
    {
        this.mSite = site;
        this.mLogger = logger;
        if (Boolean.parseBoolean(site.getEnableSelfSigned())){
            try{
                SSLContext sslcontext = SSLContexts.custom()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy())
                    .build();
                SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext,SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                CloseableHttpClient httpclient = HttpClients.custom()
                    .setSSLSocketFactory(sslsf)
                    .build();
                // Unirest.setHttpClient(httpclient);
            } catch (Exception ex) {
                logger.println("Failed to setup SSL self");
            }
        }
    }


    /** Sends the request, after doing the common configuration (accept header,
     * auth...) and returns the answer as JSON, or throws the error.
     * @param req the UniRest HttpRequest to send
     * @return the answer as JSON
     */
    private JSONObject sendRequest(HttpRequest req) throws AptlyRestException
    {
        HttpResponse<String> response;
        if(!mSite.getUsername().isEmpty()){
                req.basicAuth(mSite.getUsername(), mSite.getPassword());
        }

        req = req.header("accept", "application/json");
        try {
            response = req.asString();
        }
        catch (UnirestException ex) {
            throw new AptlyRestException("API request failed: " + ex.getMessage());
        }

        if (response.getStatus() != 200){
            throw new AptlyRestException("API request failed, response from server: " +
                                          response.getStatusText());
        }
        mLogger.printf("Aptly API response (code %d)\n%s\n",
                        response.getStatus(),
                        prettyPrintJson(response.getBody())
        );

        JSONObject json = new JSONObject();

        try {
            json = new JSONObject(response.getBody());
        }
        catch(org.json.JSONException ex){
            // grr, the upload request gives back a JSON array,
            // not object as all the others, so let the hack begin...
            try {
                JSONArray ja = new JSONArray(response.getBody());
                json.put("UploadedFiles", ja);
            }
            catch (org.json.JSONException e) {
                mLogger.printf("Response JSON parsing error <%s>, ignoring",e.getMessage());
            }
        }
        return json;
    }

    public String getAptlyServerVersion() throws AptlyRestException
    {
        String retval;
        try {
            GetRequest req = Unirest.get(mSite.getUrl() +"/api/version");
            JSONObject res = sendRequest(req);
            mLogger.println("Version response" + res.toString());
            retval = res.getString("Version");
            return retval;
        }
        catch(AptlyRestException e){
            throw e;
        }
    }

    public void uploadFiles(List<File> filepaths, String uploaddir) throws AptlyRestException
    {
        mLogger.println("upload dir name: " + uploaddir);
        // for (File file : filepaths) {
        //     mLogger.println(file.getPath());
        // }
        try {
            HttpRequestWithBody req = Unirest.post(mSite.getUrl() + "/api/files/" + uploaddir);
            MultipartBody mreq = req.multiPartContent();
            mreq = mreq.field("files[]", filepaths);
            // req = req.contentType("multipart/form-data");
            JSONObject res = sendRequest(mreq);
        }
        catch (AptlyRestException ex) {
            mLogger.printf("Failed to upload the packages: %s\n", ex.toString());
            throw ex;
        }
    }

    public void addUploadedFilesToRepo(String reponame, String uploaddir) throws AptlyRestException
    {
        // add to the repo
        try {
            HttpRequestWithBody req = Unirest.post(mSite.getUrl() +
                                            "/api/repos/"+ reponame +"/file/" + uploaddir);
            req.queryString("forceReplace", "1");
            JSONObject res = sendRequest(req);
            mLogger.printf("Uploaded packages added to repo, response: \n%s\n", prettyPrintJson(res.toString()));
        }
        catch (AptlyRestException ex) {
            mLogger.printf("Failed to add uploaded packages to repo: \n%s\n", ex.toString());
            throw ex;
        }
    }

    // update published repo
    public void updatePublishRepo(String prefix, String distribution) throws AptlyRestException
    {
        JSONObject options = new JSONObject();
        options.put("ForceOverwrite", true);
        // options.put("Signing",buildSigningJson());

        HttpResponse req = Unirest.put(mSite.getUrl() + "/api/publish/" + prefix + "/" + distribution)
                                    .header("Content-Type", "application/json")
                                    .body(options.toString())
                                    .asJson();

        mLogger.printf("Aptly API response: (code %d)\n%s\n",
                            req.getStatus(),
                            prettyPrintJson(req.getBody().toString())
        );
    }

    private String prettyPrintJson(String uglyJsonString)
    {
        String prettyJson = "";
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Object jsonObject = objectMapper.readValue(uglyJsonString, Object.class);
            prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObject);
        }
        catch (JsonProcessingException ex) {
            ex.printStackTrace();
        }

        return prettyJson;
    }

    private JSONObject buildSigningJson()
    {
        JSONObject retval = new JSONObject();
        if (!Boolean.parseBoolean(mSite.getGpgEnabled())){
            retval.put("Skip",true);
            return retval;
        }

        retval.put("Skip",false);

        if (!mSite.getPassword().isEmpty()){
            retval.put("Batch",true);
        }

        if (!mSite.getGpgKeyname().isEmpty()){
            retval.put("GpgKey",mSite.getGpgKeyname());
        }

        if (!mSite.getGpgKeyring().isEmpty()){
            retval.put("Keyring",mSite.getGpgKeyring());
        }

        if (!mSite.getGpgSecretKeyring().isEmpty()){
            retval.put("SecretKeyring",mSite.getGpgSecretKeyring());
        }

        if ("passphrase".equals(mSite.getGpgPassphraseType())){
                retval.put("Passphrase",mSite.getGpgPassphrase());
        }

        else if ("passphrasefile".equals(mSite.getGpgPassphraseType())){
                retval.put("Passphrase",mSite.getGpgPassphraseFile());
        }

        return retval;
    }
}
