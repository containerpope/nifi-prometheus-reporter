# Nifi Prometheus Reporter

A reporting task in Nifi which is capable of sending monitoring statistics as 
prometheus metrics to a prometheus pushgateway. After this, the Prometheus
server scrapes the metrics from the pushgateway. 

## Getting Started

For setting up the requirements there is a docker-compose file in docker/prometheus, that sets up the Pushgateway, the Prometheus server and a Grafana server.
After starting the docker containers nifi needs to be downloaded and the ReportingTask has to be copied into the lib directory.


A sample dashboard can be found here: [Sample Dashboard](https://grafana.com/dashboards/3294)

* The Prometheus server runs under: http://localhost:9090
* The Pushgateway runs under: http://localhost:9091
* The Grafana instance runs under: http://localhost:3000

After setting up a simple flow and the ReportingTask, the flow can be started and the results should be visible in the Grafana dashboard.

### Prerequisites

To test or use the PrometheusReportingTask the following systems should be 
setup and running.
* Running Prometheus instance
* Running Prometheus Pushgateway instance
* Running Nifi instance

The tools can be setup with Docker or manually.

### Installing

The project can be build with maven as the standard fasion of building 
nifi-processor-bundles.

## Deployment

The previously built .nar archive has to be copied into the nifi/lib directory 
and can be used after a restart of nifi.

To restart Nifi execute:
```sh
./nifi/bin/nifi.sh restart

```

## Authors

* **Matthias Jörg** - *Initial work* - [mkjoerg](https://github.com/mkjoerg)
* **Daniel Seifert** - *Initial work* - [Daniel-Seifert](https://github.com/Daniel-Seifert)
* 
