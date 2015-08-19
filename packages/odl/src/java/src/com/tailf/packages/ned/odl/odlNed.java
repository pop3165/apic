/*
Copyright @ 2015 <Cisco China Team>
   This project is maintained by Cisco China Team.
   Team members:
   		Yuanchao Su  : yuasu@cisco.com
		Fan Yang     : fyang2@cisco.com
		Qing Zhong   : qinzhong@cisco.com
		Zichuan Ma   : zicma@cisco.com
		Yinsong Xue  : yinsxue@cisco.com
		Xinyi Xu     : xinysu@cisco.com

This part of code is written by Zichuan Ma & Yinsong Xue and is maintained by Yinsong (yinsxue).
For further information, please contact Yinsong Xue (yinsxue@cisco.com) or Yuanchao Su(yuasu@cisco.com)
*/
package com.tailf.packages.ned.odl;

import java.io.IOException;
import java.net.InetAddress;

import org.apache.log4j.Logger;

import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfNamespace;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamLeaf;
import com.tailf.conf.ConfValue;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiSchemas;
import com.tailf.ncs.ResourceManager;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.ns.Ncs;
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
import com.tailf.packages.ned.odl.namespaces.odl;

public class odlNed extends NedGenericBase  {
    private String      deviceName;
    private InetAddress ip;
    private int         port;
    private String      luser;
    private boolean     trace;
    private int         connectTimeout; // msec
    private int         readTimeout;    // msec
    private int         writeTimeout;   // msec

    private Odl         controller;

    private static Logger LOGGER = Logger.getLogger(odlNed.class);
    public  Maapi                    maapi = null;
    private boolean                  wantReverse=true;

    private static MaapiSchemas schemas;
    private static MaapiSchemas.CSNode cfgCs;
    private static MaapiSchemas.CSNode rpcCs;

    public odlNed(){
        this(true);
    }

    public odlNed(boolean wantReverse){
        this.wantReverse = wantReverse;
    }


    public odlNed(String deviceName,
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
            
            LOGGER.info("CONNECTING <==");
            
            try {
                this.controller = new Odl(ip, port, worker.getRemoteUser(), worker.getPassword());
            }
            catch (Exception e) {
                LOGGER.error(e);
                worker.error(NedCmd.CONNECT_GENERIC, e.getMessage());
            }
            
            schemas = Maapi.getSchemas();
            cfgCs = schemas.findCSNode(Ncs.uri, "/devices/device/config");
            rpcCs = schemas.findCSNode(Ncs.uri, "/devices/device/rpc");
            
            NedCapability capas[] = new NedCapability[1];
            capas[0] = new NedCapability("http://tail-f.com/packages/ned/odl", "odl");

            NedCapability statscapas[] = new NedCapability[1];
            statscapas[0] = new NedCapability("http://tail-f.com/packages/ned/odl-stats",
                                              "odl-stats");

            setConnectionData(capas,
                              statscapas,
                              this.wantReverse,  // want reverse-diff
                              TransactionIdMode.NONE);

            LOGGER.info("CONNECTING ==> OK");
        }
        catch (Exception e) {
            worker.error(NedCmd.CONNECT_GENERIC, e.getMessage()," Cntc error");
        }
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
        return new String[] { "odl", "odl-stats" };
    }

    // Which identity is implemented by the class
    public String identity() {
        return "odl:odl-id";
    }

    /**
     * Is invoked by NCS to take the configuration to a new state.
     * We retrive a rev which is a transaction handle to the
     * comming write operation then we write operations towards the device.
     * If all succeded we transition to commit phase or if
     * prepare fails we transition to abort phase.
     *
     * @param w - is the processing worker. It should be used for sending
     * responses to NCS.
     * @param data is the commands for transforming the configuration to
     * a new state.
     */

    public void prepare(NedWorker worker, NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("PREPARE <==");

        for (int i = 0; i<ops.length; i++) {
            LOGGER.info("op " + ops[i]);
        }
        edit(ops);
        worker.prepareResponse();
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
     * @param w
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
     * @param w is the processing worker. It should be used for sending
     * responses to NCS. * @param data is the commands for taking the config
     * back to the previous
     * state. */

    public void abort(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
        LOGGER.info("ABORT <==");
        edit(ops);
        worker.abortResponse();
        LOGGER.info("ABORT ==> OK");
    }


    public void revert(NedWorker worker , NedEditOp[] ops)
        throws NedException, IOException {
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
            ;
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
            LOGGER.info("THANDLE:" + tHandle);
            if (maapi == null)
                maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            LOGGER.info( this.toString()  + " Attaching to Maapi " + maapi);
            maapi.attach(tHandle, 0);
            
            LOGGER.info( this.toString()  + "attached ");
            
            String confPath =
                    "/ncs:devices/device{" + deviceName + "}/config";
            
            controller.getTopology(tHandle, maapi, confPath);
            
            maapi.detach(tHandle);
            worker.showGenericResponse();
            LOGGER.info("SHOW ==> OK");
        }
        catch (Exception e) {
            throw new NedException("",e);
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
            maapi.attach(tHandle, 0);
            maapi.setElem(tHandle, new ConfInt32(345), path);
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
        try {
            if (maapi == null)
                maapi = ResourceManager.getMaapiResource(this, Scope.INSTANCE);
            String path0 =
                "/ncs:devices/device{" + deviceName + "}/live-status/" +
                "test-stats/item";

            LOGGER.info( this.toString()  + " Attaching2 to Maapi " + maapi +
                         " for " + path);
            maapi.attach(tHandle, 0);

            maapi.create(tHandle, path0 + "{k1}");
            maapi.create(tHandle, path0 + "{k2}");
            maapi.create(tHandle, path0 + "{k3}");
            maapi.detach(tHandle);

            worker.showStatsListResponse(30, null);
        }
        catch (Exception e) {
            throw new NedException("",e);
        }
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

        try {
            // Find schema node for the Enumeration in the rpc reply
                if (cmdname.equals("add-lsp")) this.controller.addLsp(p);
                if (cmdname.equals("remove-lsp")) this.controller.removeLsp(p);
                if (cmdname.equals("update-lsp")) this.controller.updateLsp(p);
                if (cmdname.equals("add-lsp-sr")) this.controller.addLspSR(p);
                if (cmdname.equals("update-lsp-sr")) this.controller.updateLspSR(p);
                if (cmdname.equals("remove-lsp-sr")) this.controller.removeLspSR(p);
           // MaapiSchemas.CSNode ecs =
             //   odlNed.schemas.findCSNode(Ncs.uri, "/devices/device/rpc", cmdname);
            //ecs = ecs.getChild(odl._operation_result);
            ConfNamespace x = new odl();
            if (cmdname.equals("get-tunnel-id")){
                    worker.commandResponse(new ConfXMLParam[] {
                        new ConfXMLParamValue(x, "tunnel-id",new ConfUInt32(this.controller.getTunnelId(p)))
                    });
            }else{
                    worker.commandResponse(new ConfXMLParam[] {
                        new ConfXMLParamLeaf(x, "operation-result")
                    });
            }

            //int a = 1;
        }
        catch (Exception e) {
            throw new NedException("", e);
        }
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
        odlNed ned = null;

        ned = new odlNed(deviceId, ip, port, luser, trace,
                       connectTimeout, readTimeout, writeTimeout,
                       mux, worker,
                       wantReverse );
        LOGGER.info("NED invoking newConnection() ==> OK");
        return ned;
    }

    public void getTransId(NedWorker w) throws NedException, IOException {
        w.error(NedCmd.GET_TRANS_ID, "getTransId", "not supported");
    }

    public void edit(NedEditOp[] ops)
        throws NedException {
        edit(ops, null);
    }

    public void edit(NedEditOp[] ops, StringBuilder dryRun)
        throws NedException {

        try {
            for (NedEditOp op: ops) {
                switch (op.getOperation()) {
                case NedEditOp.CREATED:
                    create(op, dryRun);
                    break;
                case NedEditOp.DELETED:
                    delete(op, dryRun);
                    break;
                case NedEditOp.MOVED:
                    break;
                case NedEditOp.VALUE_SET:
                    valueSet(op, dryRun);
                    break;
                case NedEditOp.DEFAULT_SET:
                    defaultSet(op, dryRun);
                    break;
                }
            }
        } catch (Exception e) {
            throw new NedException("", e);
        }
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
        // ConfKey key = (ConfKey)kp[0];
        /*
        int i = ((ConfInt32)key.elementAt(0)).intValue();
        if (dryRun == null) {
            device.rows.add(new Row(i));
        } else {
            dryRun.append("new Row[");
            dryRun.append(i);
            dryRun.append("]");
            dryRun.append('\n');
        }
        */
    }

    public void valueSet(NedEditOp op, StringBuilder dryRun)
        throws NedException  {
        ConfObject[] kp = getKP(op);
        // ConfKey key = (ConfKey)kp[1];
        // ConfTag tag = (ConfTag)kp[0];
        /*
        Row r = device.getRow(key);

        if (tag.getTag().compareTo("x") == 0) {
            ConfUInt32 v = (ConfUInt32) op.getValue();
            if (dryRun == null) {
                r.x = (int)v.longValue();
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].x = ");
                dryRun.append(v);
                dryRun.append('\n');
            }
        }
        else if (tag.getTag().compareTo("y") == 0) {
            ConfInt8 v = (ConfInt8) op.getValue();
            if (dryRun == null) {
                r.y = v.intValue();
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].y = ");
                dryRun.append(v);
                dryRun.append('\n');
            }
        }
        else if (tag.getTag().compareTo("z") == 0) {
            ConfInt8 v = (ConfInt8) op.getValue();
            if (dryRun == null) {
                r.z = v.intValue();
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].z = ");
                dryRun.append(v);
                dryRun.append('\n');
            }
        }
        else if (tag.getTag().compareTo("e") == 0) {

            MaapiSchemas.CSNode ecs =
                odlNed.schemas.findCSNode(odlNed.cfgCs, odl.uri, "row");
            ecs = odlNed.schemas.findCSNode(ecs, odl.uri, "e");
            ConfValue v = (ConfValue)op.getValue();
            String str = odlNed.schemas.valueToString(ecs.getType(), v);
            if (dryRun == null) {
                if (str.compareTo("Up") == 0) {
                    r.e = Direction.Up;
                } else if (str.compareTo("Down") == 0) {
                    r.e = Direction.Down;
                }
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].e = ");
                dryRun.append(str);
                dryRun.append('\n');
            }
        }
        */
    }


    public void defaultSet(NedEditOp op, StringBuilder dryRun)
        throws NedException  {
        ConfObject[] kp = getKP(op);
        // ConfKey key = (ConfKey)kp[1];
        // ConfTag tag = (ConfTag)kp[0];
        /*
        Row r = device.getRow(key);

        if (tag.getTagHash() == odl._y) {
            if (dryRun == null) {
                r.y =  Device.default_y;
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].y = ");
                dryRun.append(Device.default_y);
                dryRun.append('\n');
        }
        }
        else if (tag.getTagHash() == odl._z) {
            if (dryRun == null) {
                r.z = Device.default_z;
            } else {
                dryRun.append("Row[");
                dryRun.append(r.k);
                dryRun.append("].z = ");
                dryRun.append(Device.default_z);
                dryRun.append('\n');
            }
        }
        */
    }

    public void delete(NedEditOp op, StringBuilder dryRun)
        throws NedException  {
        ConfObject[] kp = getKP(op);
        // ConfKey key = (ConfKey)kp[0];
        /*
        Row r = device.getRow(key);
        if (dryRun == null) {
            device.rows.remove(r);
        } else {
            dryRun.append("delete Row[");
            dryRun.append(r.k);
            dryRun.append("]");
            dryRun.append('\n');
        }
        */
    }
}
