# <u>Project documentation</u>

# Kubernetes configuration

This document explains how this project is used.

## Basic principle

This project is intended to read a bunch of database mappings (json files) from a single folder pointed by an environement variable (or a Java property) MAPPINGS. 

- K8s will mount a virtual folder containing those files for us.
- The whole process is managed in K8s [ConfigMap](https://kubernetes.io/docs/tasks/configure-pod-container/configure-pod-configmap/). It is written in YAML.
- The ConfigMap goes beyond this project, it also install [Metabase](https://www.metabase.com/) BI software and a intermediate MongoDb.
- To makes things more accessible, the ConfigMap is extended with templating using [Helm](https://helm.sh/). Helm syntax starts with `{{ ... }}`.
- The content of each file is taken from a K8s Helm key/value pair. There is one key per file. The value is the JSON data. There is no real network drive mounting involved here. 

## How Helm templates works

Helm is based on the Golang [templating engine](https://pkg.go.dev/text/template). It provides also a repository and a versioning system on top of the templates.

- [Here](https://pkg.go.dev/text/template) is the documentation of the Golang templating engine
- [Here](https://helm.sh/docs/) is the documentation of Helm itself

### Terminology

Helm install Charts in K8s. A Chart is nothing more than a bunch of YAML files defining the configuration of your project. The directory structure should looks like this:

```
mychart/
  Chart.yaml
  values.yaml
  charts/
  templates/
  ...
```

- **values.yaml** is the default configuration for the chart.
- **Chart.yaml** define the chart itself with its dependencies.
- default values can be overriden by a sub chart or during installation (same philosophy than Springboot values). More info [here](https://helm.sh/docs/chart_template_guide/values_files/).

See [here](https://helm.sh/docs/chart_template_guide/getting_started/) more informations.

### YAML Syntax

YAML is serialization format based on a careful choice of indentation with spaces. A single missing space and everything is broken.

Special keywords can be used when you need a multiline value (see the Helm doc [here](https://helm.sh/docs/chart_template_guide/yaml_techniques/), and [that](https://stackoverflow.com/a/21699210)). This table show 9 ways to do it:

```yaml
                      >     |            "     '     >-     >+     |-     |+
-------------------------|------|-----|-----|-----|------|------|------|------  
Trailing spaces   | Kept | Kept |     |     |     | Kept | Kept | Kept | Kept
Single newline => | _    | \n   | _   | _   | _   | _    |  _   | \n   | \n
Double newline => | \n   | \n\n | \n  | \n  | \n  | \n   |  \n  | \n\n | \n\n
Final newline  => | \n   | \n   |     |     |     |      |  \n  |      | \n
Final dbl nl's => |      |      |     |     |     |      | Kept |      | Kept  
In-line newlines  | No   | No   | No  | \n  | No  | No   | No   | No   | No
Spaceless newlines| No   | No   | No  | \   | No  | No   | No   | No   | No 
Single quote      | '    | '    | '   | '   | ''  | '    | '    | '    | '
Double quote      | "    | "    | "   | \"  | "   | "    | "    | "    | "
Backslash         | \    | \    | \   | \\  | \   | \    | \    | \    | \
" #", ": "        | Ok   | Ok   | No  | Ok  | Ok  | Ok   | Ok   | Ok   | Ok
Can start on same | No   | No   | Yes | Yes | Yes | No   | No   | No   | No
line as key       |
```

On top of that, Helm blocks can start and end with a "-" to mean "chop the spaces". (More info [here](https://helm.sh/docs/chart_template_guide/control_structures/)):

```yaml
{{- if eq .Values.favorite.drink "coffee" }}
  mug: true
{{- end }}
```

Another example:

```yaml
{{- if eq .Values.favorite.drink "coffee" -}}
  mug: true
{{- end -}}
```

Here a real life example:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: "replication-config-mappings-bridge-carpates"
data:
  mappings: |-
    {{- $.Files.Get "configuration/replication/mappings/bridge-carpates.json" | nindent 6 -}}

```

- **"|-"** is used because we are injecting a multiline value (the JSON).
- **"{{-"** and **"-}}"** is used to trim the spaces around the json.
- **"nindent"** preserve the current indentation

### Chart example

Here is the Chart for this project:

```yaml
apiVersion: v2
name: "metabase"
version: 2.11.1
description: "Metabase Dashboards"
dependencies:
  - name: metabase
    version: 0.12.1
    repository: "s3://bridge-v3-helm-repository/stable-legacy"
  - name: postgresql
    alias: "postgresql-mongo-replicated"
    version: 9.4.0
    repository: "https://charts.bitnami.com/bitnami"
  - name: bridge-v3-mongo
    version: 6.3.1
    repository: "s3://bridge-v3-helm-repository/stable"
  - name: bridge-v3-secrets
    version: 4.0.3
    repository: "s3://bridge-v3-helm-repository/stable"
```

This works exactly like a **package.json** in NodeJs or a **pom.xml** in Java. 

### Values

Here is the default values for this project (`values.production.yaml`):

```yaml
metabase:
  ingress:
    enabled: true
    annotations:
      kubernetes.io/ingress.class: "bridge"
    path: "/"
    hosts:
      - metabase.leadformance.com
    tls:
      - hosts:
          - metabase.leadformance.com
  database:
    type: "postgres"
    host: "metabase-prod.c17io4i9iwit.eu-west-1.rds.amazonaws.com"
    port: "5432"
    dbname: "metabase"
```

Values are injected in templates. Templates produces the ConfigMap.

### Key/values

Helm use the hierachical structure of YAML to define variables. The following define `.Values.resources.count`

```yaml
resources:
  count: 1
```

It is also possible to use '/' in a key name:

```yaml
resources:
  nvidia.com/gpu: 1
```

This define `.Values.resources[nvidia.com/gpu]` but this time you need to use the [index](https://pkg.go.dev/text/template#hdr-Functions) function to read the value:

```yaml
index .Values.resources "nvidia.com/gpu"
```

### Includes

A ConfigMap can be huge, so we can split the YAML in multiple files using Helm [include](https://helm.sh/docs/howto/charts_tips_and_tricks/#using-the-include-function) and [print](https://helm.sh/docs/chart_template_guide/function_list/#print) functions:

```yaml
{{ include (print "YOUR_FILE_PATH") . | FUNCTION | FUNCTION| FUNCTION| FUNCTION }}
```

- `include (something) .`:  include something in the pipeline 
- `include (something) $var`:  include something in the pipeline, and also in a variable $var

The content of the expression can be passed to a chain of functions. It's like Unix pipelining (more info [here](https://helm.sh/docs/chart_template_guide/functions_and_pipelines/#pipelines)). Here a simple example where the content of a file is passed to the [sha256sum](https://helm.sh/docs/chart_template_guide/function_list/#sha256sum) function:

```yaml
spec:
  template:
    metadata:
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/ConfigMap.yaml") . | sha256sum }}
```

Most of the time, the function [indent](https://helm.sh/docs/chart_template_guide/function_list/#indent) or [nindent](https://helm.sh/docs/chart_template_guide/function_list/#nindent) is used in the pipeline to make the YAML indentation right.

## Current configuration

The configuration is stored in the repository [bridge-k8s](https://github.com/Leadformance/bridge-k8s/tree/master/metabase-dashboards/metabase). As you should know now, **the wording is incorrect**. We should replace "replication" by something like "ETL process" or "ETL".

Here an extract of `replication-config.yaml`:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: "replication-config-mappings-bridge-alps"
data:
  mappings: |-
    {{- $.Files.Get "configuration/replication/mappings/bridge-alps.json" | nindent 6 -}}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: "replication-config-mappings-bridge-bauges"
data:
  mappings: |-
    {{- $.Files.Get "configuration/replication/mappings/bridge-bauges.json" | nindent 6 -}}
```



