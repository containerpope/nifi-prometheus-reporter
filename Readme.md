# Nifi Prometheus Reporter

A reporting task in Nifi which is capable of sending monitoring statistics as 
prometheus metrics to a prometheus pushgateway. After this, the Prometheus
server scrapes the metrics from the pushgateway. 

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

## Getting Started



## Deployment

The previously built .nar archive has to be copied into the nifi/lib directory 
and can be used after a restart of nifi.


To restart Nifi execute:
```sh
./nifi/bin/nifi.sh restart

```

## Built With

* [Maven](https://maven.apache.org/) - Dependency Management

## Contributing


## Versioning


## Authors

* **Matthias Jörg** - *Initial work* - [mkjoerg](https://github.com/mkjoerg)
* **Daniel Seifert** - *Initial work* - [Daniel-Seifert](https://github.com/Daniel-Seifert)
* 
## License


## Acknowledgments

* Hat tip to anyone who's code was used
* Inspiration
* etc

