FROM apache/nifi:1.9.2

ADD https://github.com/mkjoerg/nifi-prometheus-reporter/releases/download/nifi-1.9.2/nifi-prometheus-nar-1.9.2.nar ${NIFI_BASE_DIR}/nifi-current/lib

USER root

# Setup NiFi user and create necessary directories
RUN chown -R nifi:nifi ${NIFI_BASE_DIR}/nifi-current/lib

USER nifi

