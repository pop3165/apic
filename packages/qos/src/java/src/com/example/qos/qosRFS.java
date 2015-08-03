package com.example.qos;

import com.example.qos.namespaces.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;


public class qosRFS{

	
	String src_device_name = "";
	String src_endpoint = "";
	String src_port = "";
	String dst_device_name = "";
	String dst_endpoint = "";
	String dst_port = "";
	String qos_level = "";
	
	String apic_tenant = "";
	String apic_application_profile = "";
	String apic_end_point = "";
	
	String odl_node = "";

    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws DpCallbackException
     */

    @ServiceCallback(servicePoint="qos-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws DpCallbackException {

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");
            for (NavuContainer device : managedDevices) {
                if (device.list("capability").isEmpty()) {
                    String mess = "Device %1$s has no known capabilities, " +
                                   "has sync-from been performed?";
                    String key = device.getKey().elementAt(0).toString();
                    throw new DpCallbackException(String.format(mess, key));
                }
            }
        } catch (DpCallbackException e) {
            throw (DpCallbackException) e;
        } catch (Exception e) {
            throw new DpCallbackException("Not able to check devices", e);
        }


        String servicePath = null;
        try {
            servicePath = service.getKeyPath();

            //Now get the single leaf we have in the service instance
            // NavuLeaf sServerLeaf = service.leaf("dummy");

            //..and its value (wich is a ipv4-addrees )
            // ConfIPv4 ip = (ConfIPv4)sServerLeaf.value();

            //Get the list of all managed devices.
            NavuList managedDevices =
                ncsRoot.container("devices").list("device");

            // iterate through all manage devices
            for(NavuContainer deviceContainer : managedDevices.elements()){
            	System.out.println("######"+deviceContainer.getName());
                // here we have the opportunity to do something with the
                // ConfIPv4 ip value from the service instance,
                // assume the device model has a path /xyz/ip, we could
                // deviceContainer.container("config").
                //         .container("xyz").leaf(ip).set(ip);
                //
                // remember to use NAVU sharedCreate() instead of
                // NAVU create() when creating structures that may be
                // shared between multiple service instances
            }
            try {
            	
				src_device_name = service.leaf("src-device-name").valueAsString();
				src_endpoint = service.leaf("src-endpoint").valueAsString();
				src_port = service.leaf("src-port").valueAsString();
				dst_device_name = service.leaf("dst-device-name").valueAsString();
				dst_endpoint = service.leaf("dst-endpoint").valueAsString();
				dst_port = service.leaf("dst-port").valueAsString();
				qos_level = service.leaf("qos-level").valueAsString();
				
				apic_tenant = "win_cisco";
				apic_application_profile = src_endpoint.equals("")?"ap1":src_endpoint;
				apic_end_point = src_port.equals("")?"ep1":src_port;
				
				odl_node ="pcc://110.0.0.1";
				
				if(src_device_name.contains("apic")){
					NavuLeaf deviceLeaf = service.leaf("src-device-name");
					NavuContainer device=(NavuContainer)deviceLeaf.deref().get(0).getParent();
					
					//Check QOS_LEVEL Before set
					String prio = device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").valueAsString();
					System.out.println("Before QOS service"+prio);
					
					//set QOS_LEVEL to APIC
					device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").set(qos_level);
					
					//Check QOS_LEVEL after set
					prio = device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").valueAsString();
					System.out.println("After QOS service"+prio);
				}
				
				
				if(dst_device_name.contains("odlc")){
					NavuLeaf deviceLeaf = service.leaf("dst-device-name");
					NavuContainer device=(NavuContainer)deviceLeaf.deref().get(0).getParent();
									
					//add-lsp, R1->R3->R2, next hop 13.0.0.3, 23.0.0.2
					device.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>13.0.0.3/32</ip-prefix></ip-prefix></subobject><subobject><loose>false</loose><ip-prefix><ip-prefix>23.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");
				}
				
				//enable pbts on R1
				NavuContainer device = managedDevices.elem("a9k");
				device.container("config").container("cisco-ios-xr", "interface").list("TenGigE").elem("0/0/2/0").container("service-policy").container("type").container("pbr").leaf("input").set("E-PBR");
				
            }
            catch(ConfException e){
            	System.out.println(e);
            }
        } catch (NavuException e) {
        	System.out.println(e);
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);          
        }
        return opaque;
    }


    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="qos-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="qos-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "qos";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
    

	@ServiceCallback(servicePoint = "qos-servicepoint",
	                 callType = ServiceCBType.PRE_MODIFICATION)
	public Properties preModification(ServiceContext context, ServiceOperationType operation,
	ConfPath path,
	Properties opaque)
	throws DpCallbackException{
		
		return opaque;
		
	}
	
	//we are just using post modification to modify the lsp
	@ServiceCallback(servicePoint = "qos-servicepoint",
	                 callType = ServiceCBType.POST_MODIFICATION)
	public Properties postModification(ServiceContext context, ServiceOperationType operation,
	ConfPath path,
	Properties opaque)
	throws DpCallbackException{
		
		if(ServiceOperationType.DELETE == operation){
			System.out.println("Delete ---- Modification");
			
			try{
				
				NavuList managedDevices =context.getRootNode().container("devices").list("device");
                NavuContainer odlController=managedDevices.elem("odlc");
                odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><network-topology>pcep-topology</network-topology>");
			}
			catch(Exception e){
				System.out.print(e);
			}
		}
		
		if(ServiceOperationType.UPDATE == operation){
			System.out.println("Update ---- Modification");
			
			try{
				NavuList managedDevices =context.getRootNode().container("devices").list("device");
                NavuContainer odlController=managedDevices.elem("odlc");
                
                
                
                if(qos_level.equals("level1")){
	                System.out.println("delete old(level2) lsp");
	                odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><network-topology>pcep-topology</network-topology>");
	                
	                //add lsp, R1->R2,next hop 12.0.0.2
	                System.out.println("create new(level1) lsp");
	                odlController.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>12.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");		
                }
                
                if(qos_level.equals("level2")){
                	System.out.println("delete old(level1) lsp");
                    odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><network-topology>pcep-topology</network-topology>");
                    
                    System.out.println("create new(level2) lsp");
                    //add-lsp, R1->R3->R2, next hop 13.0.0.3, 23.0.0.2
                    odlController.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>tencentlsp</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>13.0.0.3/32</ip-prefix></ip-prefix></subobject><subobject><loose>false</loose><ip-prefix><ip-prefix>23.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");
				
                    }
            }
			catch(Exception e){
				System.out.print(e);
			}
		}	
		
		return opaque;
		
	}
}
