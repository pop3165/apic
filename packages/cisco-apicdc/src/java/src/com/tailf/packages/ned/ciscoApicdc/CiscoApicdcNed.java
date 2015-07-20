package com.tailf.packages.ned.ciscoApicdc;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBinary;
import com.tailf.conf.ConfEnumeration;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiConfigFlag;
import com.tailf.maapi.MaapiSchemas.CSNode;
import com.tailf.maapi.MaapiSchemas.CSSchema;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ned.NedCapability;
import com.tailf.ned.NedCmd;
import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedException;
import com.tailf.ned.NedGenericBase;
import com.tailf.ned.NedMux;
import com.tailf.ned.NedTTL;
import com.tailf.ned.NedTracer;
import com.tailf.ned.NedWorker;
import com.tailf.ned.NedWorker.TransactionIdMode;
import com.tailf.ned.SSHSession;

public class CiscoApicdcNed extends NedGenericBase  {
    private String      deviceName;
    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private int         connectTimeout; // msec
    private int         readTimeout;    // msec
    private int         writeTimeout;   // msec
    private String baseUrl = null;
    private final String objectPrefix;
    private final String configPrefix;
    private static final String IDENTITY = "http://tail-f.com/ned/ciscoapicdc";
    private static final String MODULE   = "cisco-apicdc";
    private static final String DATE     = "2015-07-07";
    private static final String VERSION  = "3.0.0.4";


    private static Logger LOGGER = Logger.getLogger(CiscoApicdcNed.class);
    public  Maapi                    maapi = null;
    private boolean                  wantReverse=true;
    private CookieStore cookieStore = new BasicCookieStore();
    private CloseableHttpClient client;

    public class DeviceConfig implements Comparator<DeviceConfig>{
        public String classname;
        public String[] keys;
        public String[] keynames;

        Map<String,String> attributes;
        public String parentPath;

        public DeviceConfig()
        {

        }
        public DeviceConfig(String parent, String classname, String[] keys,
                String[] keynames, Map<String,String> attributes)
                        throws NumberFormatException, ConfException
        {
            this.classname = classname;
            this.keys=keys;
            this.keynames=keynames;
            this.attributes = attributes;
            this.parentPath = getParentPath(parent);
        }
        private String getParentPath(String parentPath)
                throws NumberFormatException, ConfException
        {
            if(parentPath.equals("/"))
                return "";
            String path = parentPath.replaceFirst("cisco-apicdc:", "");
            LOGGER.debug("path before: " + path);
            //Special case, object with two keys
            for(String className:PrefixMap.prefixMap2.keySet())
            {
                if(path.contains(className))
                {
                    String enumValue = CiscoApicdcNed.getTypeEnumLabel(path);
                    String firstPref =
                            PrefixMap.prefixMap2.get(className).get(0);
                    String secondPref =
                            PrefixMap.prefixMap2.get(className).get(1);
                    path = path.replaceFirst(className, firstPref);
                    //Note:this will fail if there are more than 10 enum values
                    path=path.replaceFirst(" Enum[(][0123456789][)]",
                            secondPref+enumValue);
                }
            }
            for(String className:PrefixMap.prefixMap.keySet())
            {
                path = path.replaceFirst(className,
                        PrefixMap.prefixMap.get(className));
            }
            path = path.replaceAll("\\{", "").replaceAll("\\}", "");
            path = path.replaceFirst("/apic", "");
            LOGGER.debug("path: "+path);
            return path;
        }
        public void print(){
            if(LOGGER.isDebugEnabled())
            {
                LOGGER.debug("ClassName: "+this.classname);
                LOGGER.debug("Keys: "+Arrays.toString(this.keys));
                LOGGER.debug("Attributes: "+this.attributes);
                LOGGER.debug("/ClassName: "+this.classname);
            }
        }
        @Override
        public int compare(DeviceConfig o1, DeviceConfig o2) {
            int countO1 = o1.parentPath.length() -
                    o1.parentPath.replace("/", "").length();
            int countO2 = o2.parentPath.length() -
                    o2.parentPath.replace("/", "").length();
            return countO1 - countO2;
        }
    }
    public ArrayList<String> generateXml(InetAddress ip,
            ArrayList<DeviceConfig> deviceConfig){
        ArrayList<String> output = new ArrayList<String>();
        for (DeviceConfig config: deviceConfig)
        {
            String url = "POST https://"+ip.getHostAddress()+"/api/mo/uni"+
                    config.parentPath+".xml";

            LOGGER.debug("Url: "+url);
            config.print();
//            url = "https://10.68.32.68/api/mo/uni/tn-Test-NED-3/
            //BD-Test-bd/subnet-1.2.3.4%2F/24.xml";
            StringBuilder out = new StringBuilder();
            out.append(url);
            if(config.keys!=null&&config.keynames!=null)
            {
                out.append("<"+config.classname+" "+config.keynames[0]+"=");
                out.append("\""+config.keys[0]+"\"");
                if(config.keys.length>1)
                {
                    out.append(" type"+"=");   //+config.keynames[0]
                    out.append("\""+config.keys[1]+"\"");
                }
            }
            else
            {
                out.append("<"+config.classname+" ");
            }
            if (config.attributes!=null){
                for (Map.Entry<String, String> entry :
                    config.attributes.entrySet()) {
                    String attkey = entry.getKey();
                    String attvalue = entry.getValue();
                    out.append(" "+attkey+"=\""+attvalue+"\"");
                }
            }
            out.append("/>");
            output.add(out.toString());
        }
        return output;
    }


    public CiscoApicdcNed(){
        //this(true);
        this.configPrefix = null;
        this.objectPrefix = null;

    }

    public CiscoApicdcNed(boolean wantReverse){
        this.wantReverse = wantReverse;
        this.configPrefix = null;
        this.objectPrefix = null;

    }


    public CiscoApicdcNed(String deviceName,
            InetAddress ip,
            int port,
            String luser,
            boolean trace,
            int connectTimeout,
            int readTimeout,
            int writeTimeout,
            NedMux mux,
            NedWorker worker,
            boolean wantReverse)  {
        this.configPrefix = "/ncs:devices/device{"+deviceName+"}/config";
        this.objectPrefix = "/ncs:devices/device{"+deviceName+"}"
                + "/cisco-apicdc:objectInfo";


        try {
            this.deviceName = deviceName;
            this.ip = ip;
            this.port = port;
            this.luser = luser;
            this.trace = trace;
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
            this.writeTimeout = writeTimeout;
            this.wantReverse = wantReverse;


            /*
             * This map contains the root classes for all the
             * top lists in the YANG model. The root class is used
             * as parent when creating a top list entry.
             *
             * This list must be updated each time a new top list
             * is defined in the YANG model.
             */

            LOGGER.info("CONNECTING <==");
            trace(worker, "NED VERSION: cisco-apicdc " +
                    VERSION + " " + DATE, "out");


            //TODO: fix proper certificate handling
            SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(new TrustSelfSignedStrategy());
            SSLContext context = getSSLContext(builder);

            SSLConnectionSocketFactory sslSF = new SSLConnectionSocketFactory(
                    context,
                    new NoopHostnameVerifier());

            client = HttpClients.custom()
                    .setSSLSocketFactory(sslSF)
                    .setDefaultCookieStore(cookieStore)
                    .build();

            baseUrl = "https://"+ip.getHostAddress();
            String urlString = baseUrl+"/api/aaaLogin.xml";
            LOGGER.debug("url: "+urlString);

            String input = "<aaaUser name="+worker.getRemoteUser()+
                    " pwd="+worker.getPassword()+"/>";

            httpPost(urlString, input);

            /*
             * Setup NED capabilities.
             *
             * The APIC device displays attributes with default values set as
             * any other attribute. Hence, this NED has the "with-defaults"
             * feature in NCS enabled.
             */
            NedCapability capas[] = new NedCapability[2];
            capas[0] = new NedCapability("", IDENTITY, MODULE, "", DATE, "");
            capas[1] = new NedCapability(
                    "urn:ietf:params:netconf:capability" +
                    ":with-defaults:1.0?basic-mode=report-all",
                    "urn:ietf:params:netconf:capability:with-defaults:1.0",
                    "",
                    "",
                    "",
                    "");

            NedCapability statscapas[] = new NedCapability[1];
            statscapas[0] = new NedCapability
                    ("http://tail-f.com/ned/ciscoapicdc-stats",
                            "cisco-apicdc-stats");

            setConnectionData(capas,
                    statscapas,
                    this.wantReverse,  // want reverse-diff
                    TransactionIdMode.NONE);

            LOGGER.info("CONNECTING ==> OK");
        }
        catch (Exception e) {
            LOGGER.error(e);
            worker.error(NedCmd.CONNECT_GENERIC, e.getMessage());
        }
    }

    public void trace(NedWorker worker, String msg, String direction) {
        if (trace) {
            worker.trace("-- " + msg + " --\n", direction, device_id());
        }
    }

    private SSLContext getSSLContext(SSLContextBuilder builder)
    {
        SSLContext context = null;
        try {
            builder.useProtocol("TLSv1.2");
            context = builder.build();
            return context;
        }
        catch (GeneralSecurityException e)
        {
            LOGGER.debug("Failed to initialize SSLContext with TLSv1.2");
        }

        try {
            builder.useProtocol("TLSv1.1");
            context = builder.build();
            return context;
        }
        catch (GeneralSecurityException e)
        {
            LOGGER.debug("Failed to initialize SSLContext with TLSv1.1");
        }

        try {
            builder.useProtocol("TLSv1");
            context = builder.build();
            return context;
        }
        catch (GeneralSecurityException e)
        {
            LOGGER.debug("Failed to initialize SSLContext with TLSv1");
        }

        return context;
    }

    private String httpPost(String uri, String input)
    {
        InputStream in ;
        StringEntity entity = new StringEntity(input, ContentType.create(
                "application/xml", Consts.UTF_8));
        entity.setChunked(true);
        HttpPost httppost = new HttpPost(
                uri);

        httppost.setEntity(entity);

        try {
            HttpResponse response = client.execute(httppost);
            LOGGER.debug("response:\n"+response.toString());
            if(response.getStatusLine().getStatusCode()!=200)
            {
                throw new RuntimeException(response.getStatusLine().toString());
            }
            in=response.getEntity().getContent();
            String body = IOUtils.toString(in);
            LOGGER.trace("body:\n"+body);
            return body;
        } catch (ClientProtocolException e) {
            LOGGER.error(e);
            throw new RuntimeException("http POST failed. ", e);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException("http POST failed. ", e);
        }
    }
    private String httpGet(String uri)
    {
        InputStream in ;
        HttpGet httpGet = new HttpGet( uri);

        LOGGER.debug("httpPost2: "+uri);
        try {
            HttpResponse response = client.execute(httpGet);
            LOGGER.debug("response:\n"+response.toString());
            if(response.getStatusLine().getStatusCode()!=200)
            {
                throw new RuntimeException(response.getStatusLine().toString());
            }
            in=response.getEntity().getContent();
            String body = IOUtils.toString(in);
            LOGGER.trace("body:\n"+body);
            return body;
        } catch (ClientProtocolException e) {
            LOGGER.error(e);
            throw new RuntimeException("http GET failed. ", e);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException("http GET failed. ", e);
        }
    }

    private String getAuthResponse(BufferedInputStream input)
            throws IOException {
        //Log input from device
        input.mark(Integer.MAX_VALUE);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                input));
        StringBuilder output = new StringBuilder();
        String out;
        while (( out = br.readLine()) != null)
        {
            output.append(out);
        }
        input.reset();
        if(LOGGER.isDebugEnabled())
        {
            LOGGER.debug(output.toString());
        }
        return output.toString();
    }


    public String device_id() {
        return deviceName;
    }

    // should return "cli" or "generic"
    public String type() {
        return "generic";
    }
    // Which YANG modules are covered by the class
    public String [] modules() {
        LOGGER.info("modules");
        return new String[] { "cisco-apicdc", "cisco-apicdc-stats" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "cisco-apicdc:cisco-apicdc-id";
    }

    /**
     * Is invoked by NCS to take the configuration to a new state.
     * We retrive a rev which is a transaction handle to the
     * comming write operation then we write operations towards the device.
     * If all succeded we transition to commit phase or if
     * prepare fails we transition to abort phase.
     *
     * @param worker - is the processing worker. It should be used for sending
     * responses to NCS.
     * @param ops is the commands for transforming the configuration to
     * a new state.
     */

    public void prepare(NedWorker worker, NedEditOp[] ops)
            throws NedException, IOException {
        LOGGER.info("PREPARE <==");

        for (int i = 0; i<ops.length; i++) {
            LOGGER.info("op " + ops[i]);
        }
        doEdit(ops);
        worker.prepareResponse();
    }

    private void doEdit(NedEditOp[] ops) throws NedException,
            MalformedURLException, IOException, ProtocolException {
        StringBuilder command = new StringBuilder();


        ArrayList<String> commandList = editCommands(ops,command);

        for(String aCommand:commandList)
        {
            String urlString = aCommand.substring(5, aCommand.indexOf("<"));
            aCommand = aCommand.substring(aCommand.indexOf("<"));
            LOGGER.debug("url: "+urlString);
            LOGGER.debug("aCommand: "+aCommand);
            httpPost(urlString, aCommand);
        }
    }

    /**
     * Is invoked by NCS to ask the NED what actions it would take towards
     * the device if it would do a prepare.
     *
     * The NED can send the preformatted output back to NCS through the
     * call to  {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     * prepareDryResponse()}
     *
     * The Ned should invoke the method
     * {@link com.tailf.ned.NedWorker#prepareDryResponse(String)
     *   prepareDryResponse()} in <code>NedWorker w</code>
     * when the operation is completed.
     *
     * If the functionality is not supported or an error is detected
     * answer this through a call to
     * {@link com.tailf.ned.NedWorker#error(int,String,String) error()}
     * in <code>NedWorker w</code>.
     *
     * @param worker
     *    The NedWorker instance currently responsible for driving the
     *    communication
     *    between NCS and the device. This NedWorker instance should be
     *    used when communicating with the NCS, ie for sending responses,
     *    errors, and trace messages. It is also implements the
     *    {@link NedTracer}
     *    API and can be used in, for example, the {@link SSHSession}
     *    as a tracer.
     *
     * @param ops
     *    Edit operations representing the changes to the configuration.
     */
    public void prepareDry(NedWorker worker, NedEditOp[] ops)
            throws NedException {
        StringBuilder dryRun = new StringBuilder();

        LOGGER.info("PREPARE DRY <==");
        edit(ops, dryRun);
        try {
            worker.prepareDryResponse(dryRun.toString());
        }
        catch (IOException e) {
            throw new NedException("Internal error when calling "+
                    "prepareDryResponse: "+
                    e.getMessage());
        }
        LOGGER.info("PREPARE DRY ==> OK");
    }

    public void commit(NedWorker worker, int timeout)
            throws NedException, IOException {
        LOGGER.info("COMMIT <==");
        worker.commitResponse();
    }

    /**
     * Is invoked by NCS to abort the configuration to a previous state.
     *
     * @param worker is the processing worker. It should be used for sending
     * responses to NCS. * @param data is the commands for taking the config
     * back to the previous
     * state. */

    public void abort(NedWorker worker , NedEditOp[] ops0)
            throws NedException, IOException {

        NedEditOp[] ops = ops0;
        if (Conf.LIBVSN >= 0x05040000) {
            // For NCS 3.4 and later editOps are supplied in schema order for
            // abort and revert. Since this code was written before that,
            // we have to reverse order for newer NCS releases
            List<NedEditOp> olist = Arrays.asList(ops0);
            Collections.reverse(olist);
            ops = olist.toArray(new NedEditOp[0]);
        }

        LOGGER.info("ABORT <==");
        edit(ops);
        worker.abortResponse();
        LOGGER.info("ABORT ==> OK");
    }


    public void revert(NedWorker worker , NedEditOp[] ops0)
            throws NedException, IOException {

        NedEditOp[] ops = ops0;
        if (Conf.LIBVSN >= 0x05040000) {
            // For NCS 3.4 and later editOps are supplied in schema order for
            // abort and revert. Since this code was written before that,
            // we have to reverse order for newer NCS releases
            List<NedEditOp> olist = Arrays.asList(ops0);
            Collections.reverse(olist);
            ops = olist.toArray(new NedEditOp[0]);
        }

        LOGGER.info("REVERT <==");
        edit(ops);
        worker.revertResponse();
        LOGGER.info("REVERT ==> OK");
    }


    public void persist(NedWorker worker)
            throws NedException, IOException {
        LOGGER.info("PERSIST <==");
        worker.persistResponse();

    }

    public void close(NedWorker worker)
            throws NedException, IOException {
        close();
    }

    public void close() {
        LOGGER.info("CLOSE <==");
        try {
            if (maapi != null)
                ResourceManager.unregisterResources(this);
        }
        catch (Exception e) {
            throw new RuntimeException("close() failed. ", e);
        }
        LOGGER.info("CLOSE ==> OK");
    }

    /*
     * The generic show command is to
     * grab all configuration from the device and
     * populate the transaction handle  passed to us.
     **/

    public void show(NedWorker worker, int tHandle)
            throws NedException, IOException {
        try {
            LOGGER.info("SHOW <==");
            if (maapi == null)
            {
                maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            }
            getCompleteConfig(worker, tHandle);

            worker.showGenericResponse();
            LOGGER.info("SHOW ==> OK");
        }
        catch (Exception e) {
            LOGGER.error(e);
            throw new NedException("",e);
        }
    }


    private String getCompleteConfig(NedWorker worker, int tHandle)
            throws IOException, ConfException, ParserConfigurationException,
            SAXException, TransformerException {
        String data = getConfigData("fvTenant");
        String totalData = data;
        loadConfig(worker, tHandle, data);

        data = getConfigData("vmmProvP");
        totalData = totalData + data;
        loadConfig(worker, tHandle, data);

        data = getConfigData("infraInfra");
        totalData = totalData + data;
        loadConfig(worker, tHandle, data);

        return totalData;
    }

    private void getSyncData(NedWorker worker, int tHandle)
            throws IOException, ConfException, ParserConfigurationException,
            SAXException, TransformerException {

        String result = getCompleteConfig(worker, tHandle);
        LOGGER.debug("Sync data result:\n"+result);

    }

    private String getConfigData(String subTreeClass)
            throws ParserConfigurationException,
            SAXException, TransformerException, IOException {

        //Query device for all top level subTreeClass
        String urlString = baseUrl+"/api/mo/uni.xml";
        String query =
                "query-target=subtree&target-subtree-class="+subTreeClass;
        LOGGER.debug("url: "+urlString);
        String input = httpGet(urlString+"?"+query);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(input.getBytes());
        //Convert input from device to ncs form
        XmlUtil util = new XmlUtil(baseUrl, deviceName, client);
        String result = util.getDeviceConfig(inputStream);
        LOGGER.debug("Finished result:\n"+result);
        getAuthResponse(
                new BufferedInputStream(inputStream));
        return result;
    }

    private void loadConfig(NedWorker worker, int tHandle, String config)
            throws IOException, ConfException
    {
        maapi.attach(tHandle, 0, worker.getUsid());
        try {
            EnumSet<MaapiConfigFlag> cmdflags =
                    EnumSet.of(
                            MaapiConfigFlag.MAAPI_CONFIG_XML,
                            MaapiConfigFlag.MAAPI_CONFIG_MERGE,
                            MaapiConfigFlag.MAAPI_CONFIG_XML_LOAD_LAX);

            /*
             * Load the entire xml config into ncs
             */
            maapi.loadConfigCmds(tHandle, cmdflags, config, "");

        } finally {
            maapi.detach(tHandle);
        }
    }


    /*
     * This method must at-least populate the path it is given,
     * it may do more though
     */

    public void showStats(NedWorker worker, int tHandle, ConfPath path)
            throws NedException, IOException {
        try {
            if (maapi == null)
                maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            LOGGER.info( this.toString()  + " Attaching to Maapi " + maapi +
                    " for " + path);
            maapi.attach(tHandle, 0, worker.getUsid());
            //TODO:
            maapi.detach(tHandle);

            NedTTL ttl = new NedTTL(path,0);
            worker.showStatsResponse(new NedTTL[]{ttl});
        }
        catch (Exception e) {
            throw new NedException("",e);
        }
    }

    /*
     *   This method must at-least fill in all the keys of the list it
     *   is passed, it may do more though. In this example  code we
     *   choose to not let the code in showStatsList() fill in the full
     *   entries, thus forcing an invocation of showStats()
     */

    public void showStatsList(NedWorker worker, int tHandle, ConfPath path)
            throws NedException, IOException {
    }

    public boolean isAlive() {
        return true;
    }

    public void reconnect(NedWorker worker) {
        LOGGER.info("RECONNECT ==> OK");
    }

    public boolean isConnection(String deviceId,
            InetAddress ip,
            int port,
            String luser,
            boolean trace,
            int connectTimeout, // msecs
            int readTimeout,    // msecs
            int writeTimeout) { // msecs
        return ((this.deviceName.equals(deviceName)) &&
                (this.ip.equals(ip)) &&
                (this.port == port) &&
                (this.luser.equals(luser)) &&
                (this.trace == trace) &&
                (this.connectTimeout == connectTimeout) &&
                (this.readTimeout == readTimeout) &&
                (this.writeTimeout == writeTimeout));
    }

    /*
     * If the device has commands, i,e reboot etc that are - just - commands
     * that do not manipulate the configuration, we model those commands
     * in the YANG model, and get invoked here. The task of this code is to
     * look at the input params, invoke the cmd on the device and return
     * data - according to the YANG model
     *
     */
    public void command(NedWorker worker, String cmdname, ConfXMLParam[] p)
            throws NedException, IOException {
        //TODO: not supported
    }

    /**
     * Establish a new connection to a device and send response to
     * NCS with information about the device.
     *
     * @param deviceId name of device
     * @param ip address to connect to device
     * @param port port to connect to
     * @param luser name of local NCS user initiating this connection
     * @param trace indicates if trace messages should be generated or not
     * @param connectTimeout in milliseconds
     * @param readTimeout in milliseconds
     * @param writeTimeout in milliseconds
     * @return the connection instance
     **/
    public NedGenericBase newConnection(String deviceId,
            InetAddress ip,
            int port,
            String luser,
            boolean trace,
            int connectTimeout, // msecs
            int readTimeout,    // msecs
            int writeTimeout,   // msecs
            NedMux mux,
            NedWorker worker ) {
        LOGGER.info("newConnection() <==");
        CiscoApicdcNed ned = null;

        ned = new CiscoApicdcNed(deviceId, ip, port, luser, trace,
                connectTimeout, readTimeout, writeTimeout,
                mux, worker,
                wantReverse );
        LOGGER.info("NED invoking newConnection() ==> OK");
        return ned;
    }

    public void getTransId(NedWorker worker) throws NedException, IOException,
        ParserConfigurationException, SAXException, TransformerException,
        NoSuchAlgorithmException
    {
        String data = getConfigData("fvTenant");

        data = data + getConfigData("vmmProvP");

        data = data + getConfigData("infraInfra");

        // Calculate MD5 checksum
        byte[] bytes = data.getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] theDigest = md.digest(bytes);
        BigInteger md5Number = new BigInteger(1, theDigest);
        String md5String = md5Number.toString();

        worker.getTransIdResponse(md5String);
        LOGGER.info("GETTRANSID ==> OK");
    }


    /**
     * Extracts the key name of a list
     *
     * @param path - the path to the list
     *
     * @return the key name
     * @throws Exception
     */
    private String
    getKeyName(String path) throws Exception {

        ConfPath cp = new ConfPath(this.configPrefix + path);
        ConfObject[] kp = cp.getKP();
        ConfTag tag = (ConfTag)kp[kp.length-1];

        CSSchema schema =
                Maapi.getSchemas().findCSSchemaByPrefix(tag.getPrefix());
        CSNode node =
                Maapi.getSchemas().findCSNode(schema.getNS(), cp.toString());
        if(node.getKeys().size()>0)
        {
            return node.getKeys().get(0).getTag();
        }
        else
        {
            //TODO: Doesn't work. case when single attribute is set.
            return null;
        }
    }

    /**
     * Trims one or many elements of a path.
     * @param kp    - array describing the full path
     * @param offs  - number of elements to trim
     *
     * @return trimmed path.
     */
    private String
    trimPath(ConfObject[] kp, int offs) {

        ConfObject[] newKp = new ConfObject[kp.length-offs];
        for (int j= 0; j < newKp.length; j++) {
            LOGGER.debug("kp[j+offs]: "+kp[j+offs]);
            newKp[j] = kp[j+offs];
        }

        return new ConfPath(newKp).toString();
    }

    public void edit(NedEditOp[] ops)
            throws NedException {
        LOGGER.debug("ops:\n"+Arrays.toString(ops));
        try {
            doEdit(ops);
        } catch (MalformedURLException e) {
            LOGGER.error(e);
            throw new RuntimeException("edit() failed. ", e);
        } catch (ProtocolException e) {
            LOGGER.error(e);
            throw new RuntimeException("edit() failed. ", e);
        } catch (IOException e) {
            LOGGER.error(e);
            throw new RuntimeException("edit() failed. ", e);
        }
    }


    public void edit(NedEditOp[] ops, StringBuilder dryRun)
            throws NedException {

        ArrayList<String> commandList = editCommands(ops, dryRun);
        dryRun.append( commandList);
    }

    private ArrayList<String> editCommands(NedEditOp[] ops,
            StringBuilder dryRun) throws NedException {
        Map<String,String> attributes;
        String childPath;
        ConfPath cp;
        ConfObject[] kp;
        String parentPath;
        String className;
        String attrValue;
        String attrName;

        ArrayList<DeviceConfig> commandList = new ArrayList<DeviceConfig>();
        try {
            for (int i = 0; i < ops.length; i++) {
                attributes = new HashMap<String,String>();
                if (ops[i].getOpDone()) {
                    continue;
                }

                cp = ops[i].getPath();
                kp = cp.getKP();
                LOGGER.debug(Arrays.toString(kp));
//                formatPath(kp);

                attributes.clear();

                LOGGER.debug("Op: "+ops[i].getOperation());

                switch (ops[i].getOperation()) {
                case NedEditOp.DELETED:
                    attributes.put("status", "deleted");
                case NedEditOp.CREATED:
                case NedEditOp.VALUE_SET:
                case NedEditOp.MODIFIED:
//                case NedEditOp.DEFAULT_SET:
                    /*
                     * Create new instance.
                     *
                     * Extract paths and class name of instance
                     * kp[0] key value
                     * kp[1] class name
                     * kp[2] first element of parent path
                     */
                    childPath = cp.toString();
                    parentPath = trimPath(kp,2);

                    className = kp[1].toString();
                    className = className.replaceAll("cisco-apicdc:", "");

                    /*
                     * Extract the key name and value of this instance.
                     * Shall be appended to the attribute list.
                     */
                    LOGGER.debug("childPath: "+childPath);
                    String[] keyNames = {getKeyName(childPath)};
                    String[] keys = getKeys(kp);

                    /*
                     * If delete of attribute...
                     */
                    if(keyNames[0] == null && ops[i].getOperation() ==
                            NedEditOp.DELETED)
                    {
                        //first remove status deleted
                        attributes.remove("status");
                        //fix parentPath,className,key,keyName
                        keys[0] = className.replaceAll("\\{", "").
                                replaceAll("\\}", "");
                        keyNames[0]=getKeyName(childPath.substring(0,
                                childPath.lastIndexOf("/")));
                        className = parentPath.replaceAll("/cisco-apicdc:", "");
                        parentPath = "/";
                        attrName = childPath.substring(
                                childPath.lastIndexOf("/")+1);
                        attrValue = "";  //Can't delete, set to empty

                        attributes.put(attrName, attrValue);
                    }
                    else
                    {
                        /*
                         * Append any other instance attribute to the same
                         * attribute list.
                         * The attributes will be sent to the device bundled
                         * with the create instance message.
                         */
                        for (int j=i+1; j < ops.length; j++,i++) {
                            LOGGER.debug("Op[j]: "+ops[j].getOperation());
                            if ((ops[j].getOperation() != NedEditOp.VALUE_SET))
                            {
                                break;
                            }
                            LOGGER.debug(Arrays
                                    .toString(ops[j].getPath().getKP()));
                            attrName = ops[j].getPath().getKP()[0].toString().
                                    replaceAll("cisco-apicdc:", "");
                            if(ops[j].getValue().getClass() ==
                                    ConfEnumeration.class)
                            {
                                ConfEnumeration val =
                                        (ConfEnumeration)ops[j].getValue();
                                String pathBase = "/ncs:devices/ncs:device{"+
                                        deviceName+"}/ncs:config"+
                                        ops[j].getPath().toString();
                                attrValue =
                                        ConfEnumeration.
                                        getLabelByEnum(pathBase, val);
                            }
                            else
                            {
                                if(ops[j].getValue().getClass()==
                                        ConfBinary.class)
                                {
                                    attrValue = ((ConfBinary)ops[j].getValue())
                                            .toHexListString();
                                }
                                else
                                {
                                    attrValue = ops[j].getValue().toString();
                                }
                            }
                            if(ops[j].getPath().getKP()[1].getClass()
                                    == ConfTag.class)
                            {
                                //This is not a list, it's a container.
                                //Need to calculate parent path.
                                String parentP =
                                        trimPath(ops[j].getPath().getKP(),2);
                                String classN =
                                        ops[j].getPath().getKP()[1].toString();
                                classN = classN
                                              .replaceAll("cisco-apicdc:", "");
                                if(LOGGER.isTraceEnabled())
                                {
                                    LOGGER.debug("New parentpath:\n"+parentP);
                                    LOGGER.debug("New className: "+classN);
                                    LOGGER.debug(attrValue);
                                }
                                HashMap<String,String> attrs =
                                        new HashMap<String,String>();
                                attrs.put(attrName, attrValue);
                                LOGGER.debug("attributes: "+attrs);
                                DeviceConfig configuration =
                                        new DeviceConfig(parentP,classN,
                                                null,null,attrs);
                                commandList.add(configuration);
                            }
                            else
                            {
                                attributes.put(attrName, attrValue);
                            }
                        }
                    }

                    //Classes are never modified, only attributes.
                    if(ops[i].getOperation()==NedEditOp.MODIFIED&&
                            attributes.size()<1)
                    {
                        break;
                    }

                    /*
                     * Send create instance message to device.
                     */
                    if(LOGGER.isDebugEnabled())
                    {
                        LOGGER.debug("Add: " + Arrays.toString(keys));
                        LOGGER.debug("parentPath: "+parentPath+
                                "\nclassName: "+className+
                                "\nkeys: "+Arrays.toString(keys)+
                                "\nkeyNames: "+Arrays.toString(keyNames)+
                                "\nattributes: "+attributes);
                        LOGGER.debug("------------ attributes -------------");
                        LOGGER.debug(
                                Arrays.toString(attributes.keySet().toArray()));
                        LOGGER.debug(
                                Arrays.toString(attributes.values().toArray()));
                    }
                    if(keys!=null)
                    {
                        DeviceConfig configuration =
                            new DeviceConfig(parentPath,className,keys,
                                    keyNames,attributes);
                        commandList.add(configuration);
                    }
                    break;
                case NedEditOp.MOVED:
                    break;
                case NedEditOp.DEFAULT_SET:
                    defaultSet(ops[i], dryRun);
                    break;
                }
            }
        } catch (Exception e) {
            throw new NedException("", e);
        }
        Collections.sort(commandList, new DeviceConfig());;
        return generateXml(this.ip, commandList);
    }

    private String[] getKeys(ConfObject[] kp) throws ConfException {
        if(kp[0].toString().contains("Enum"))
        {
            String keys[] = kp[0].toString().replaceAll("\\{(.*?)\\}", "$1")
                    .split(" ");
            if(keys.length>1)
            {
                keys[1] = getTypeEnumLabel(keys[1]);
            }

            return keys;
        }
        else
        {
            String key = kp[0].toString();
            key = key.replaceAll("\\{(.*?)\\}", "$1");
            String[] keys = { key };
            return keys;
        }
    }

    /**
     *
     * @param key - supposed to be of format "Enum(0)"
     * @return
     * @throws ConfException
     * @throws NumberFormatException
     */
    private static String getTypeEnumLabel(String key) throws ConfException,
            NumberFormatException {
        String enumVal = key.substring(key.indexOf("Enum(")+5,
                key.indexOf(")",key.indexOf("Enum(")+5));
        LOGGER.debug("enumVal: "+enumVal);

        ConfEnumeration val =
                new ConfEnumeration(Integer.parseInt(enumVal));
        String pathBase = "/ncs:devices/ncs:device/ncs:config"+
                "/cisco-apicdc:apic/infraInfra/infraNodeP/infraLeafS"+
                "/type";
        return ConfEnumeration.getLabelByEnum(pathBase, val);
    }

    private ConfObject[] getKP(NedEditOp op) throws NedException {
        ConfPath cp = op.getPath();
        ConfObject[] kp;

        try {
            kp = cp.getKP();
        } catch (Exception e) {
            throw new NedException("Internal error, cannot get key path: "
                    +e.getMessage());
        }
        return kp;
    }

    public void create(NedEditOp op, StringBuilder dryRun)
            throws NedException  {

        ConfObject[] kp = getKP(op);
        LOGGER.info("Create!!!"+op.getPath() + op.getValue());
        dryRun.append("<fvTenant name=\"" +op.getValue()+"\"/>");
    }

    public void valueSet(NedEditOp op, StringBuilder dryRun)
            throws NedException  {
        ConfObject[] kp = getKP(op);
        ConfKey key = (ConfKey)kp[1];

    }


    public void defaultSet(NedEditOp op, StringBuilder dryRun)
            throws NedException  {
        ConfObject[] kp = getKP(op);
        LOGGER.info("default set for " + op.getPath());
        ConfKey key = (ConfKey)kp[1];

    }

    public void delete(NedEditOp op, StringBuilder dryRun)
            throws NedException  {
        ConfObject[] kp = getKP(op);
        ConfKey key = (ConfKey)kp[0];
        if (dryRun == null) {
        } else {
            //            dryRun.append("delete Row[");
        }
    }
}
