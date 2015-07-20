
JAVA_PACKAGE = com.tailf.packages.ned.odl
JDIR = $(shell echo $(JAVA_PACKAGE) | sed 's/\./\//g')

NS  = namespaces

JFLAGS = --java-disable-prefix \
         --exclude-enums \
         --fail-on-warnings \
         --java-package $(JAVA_PACKAGE).$(NS) \
         --emit-java java/src/$(JDIR)/namespaces

all:	fxs javac

# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

SRC = $(wildcard yang/*.yang)

NFXS = ncsc-out/modules/fxs

fxs:	$(NFXS)/odl.fxs $(NFXS)/odl-stats.fxs

$(NFXS)/odl.fxs:	yang/odl.yang
	$(NCSC) --ncs-compile-module $< \
	        --ncs-device-dir ncsc-out \
	        --ncs-skip-statistics \
	        --ncs-device-type generic-ned && cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/odl.java $@

$(NFXS)/odl-stats.fxs:	yang/odl-stats.yang
	$(NCSC) --ncs-compile-module $< \
	        --ncs-device-dir ncsc-out \
	        --ncs-skip-template \
	        --ncs-device-type generic-ned && cp $@ ../load-dir
	$(NCSC) $(JFLAGS)/odlStats.java $@

javac:
	cd java && ant -q all

clean:
	rm -rf  ../load-dir/*
	rm -f ../shared-jar/*.jar
	cd java && ant -q clean || true
	rm -f java/src/$(JDIR)/$(NS)/*.java
	rm -f ./ncsc-out/modules/fxs/*
