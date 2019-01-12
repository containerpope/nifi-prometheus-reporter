# Configuration

Open your Nifi under http://localhost:8080/nifi and go to the menu in the upper right:
![Controller Settings](./img/00-controller-settings.png)

Go to Controller Settings and add a new reporting task:
![Add Reporting Task](./img/01-add-reporting-task.png)

After the task is added, it has to be configured:
![Configure Reporting Task](./img/02-configure-reporting-task.png)

The following settings can be edited: (See the help texts for more details)
![Settings Reporting Task](./img/03-settings-reporting-task.png)

After the reporting task is configured, run it:
![Run Reporting Task](./img/04-run-reporting-task.png)

If everything went well, the prometheus pushgateway (localhost:9091) should now provide your metrics:
![Check Pushgateway](./img/05-pushgateway-view.png)

After this the metrics can be scraped by prometheus and then visualized in Grafana.