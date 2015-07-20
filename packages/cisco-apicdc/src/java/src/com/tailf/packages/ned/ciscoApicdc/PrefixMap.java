package com.tailf.packages.ned.ciscoApicdc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class PrefixMap {
    public static final HashMap<String,String> prefixMap;
    static{
        prefixMap = new HashMap<String, String>();
        prefixMap.put("fvTenant", "tn-");
        prefixMap.put("fvAp", "ap-");
        prefixMap.put("fvAEPg", "epg-");
        prefixMap.put("fvRsBd", "rsbd-");
        prefixMap.put("fvRsPathAtt", "rspathAtt-");
        prefixMap.put("fvBD", "BD-");
        prefixMap.put("fvSubnet", "subnet-");
        prefixMap.put("fvRsCtx", "rsctx-");
        prefixMap.put("fvCEp", "cep-");
        prefixMap.put("vzBrCP", "brc-");
        prefixMap.put("vzSubj", "subj-");
        prefixMap.put("vzRsSubjFiltAtt", "rssubjFiltAtt-");
        prefixMap.put("vzEntry", "e-");
        prefixMap.put("vzFilter", "flt-");
        prefixMap.put("fvRsCons", "rscons-");
        prefixMap.put("fvRsProv", "rsprov-");
        prefixMap.put("fvRsDomAtt", "rsdomAtt-");
        prefixMap.put("vmmCtrlrP", "ctrlr-");
        prefixMap.put("vmmProvP", "vmmp-");
        prefixMap.put("vmmDomP", "dom-");
        prefixMap.put("l3extRsPathL3OutAtt", "rspathL3OutAtt-");
        prefixMap.put("l3extOut", "out-");
        prefixMap.put("l3extLNodeP", "lnodep-");
        prefixMap.put("l3extLIfP", "lifp-");
        prefixMap.put("l2extRsPathL2OutAtt", "rspathL2OutAtt-");
        prefixMap.put("l2extOut", "l2out-");
        prefixMap.put("l2extRsEBd", "rseBd");
        prefixMap.put("l2extRsL2DomAtt", "rsl2DomAtt");
        prefixMap.put("l2extLNodeP", "lnodep-");
        prefixMap.put("l2extLIfP", "lifp-");
        prefixMap.put("l2extInstP", "instP-");
        prefixMap.put("infraInfra", "infra");
        prefixMap.put("infraNodeP", "nprof-");
        prefixMap.put("infraAccPortP", "accportprof-");
        prefixMap.put("infraNodeBlk", "nodeblk-");
        prefixMap.put("infraPortBlk", "portblk-");
        prefixMap.put("infraAccBndlGrp", "accbundle-");
        prefixMap.put("infraAccPortGrp", "accportgrp-");
        prefixMap.put("infraFuncP", "funcprof");
    }

    public static final HashMap<String, List<String>> prefixMap2;
    static{
        prefixMap2 = new HashMap<String, List<String>>();
        prefixMap2.put("infraLeafS", Arrays.asList( "leaves-", "-typ-"));
        prefixMap2.put("infraHPortS",Arrays.asList("hports-", "-typ-"));
    }
}
