# [OpenCompany](https://github.com/open-company) Search Service

[![MPL License](http://img.shields.io/badge/license-MPL-blue.svg?style=flat)](https://www.mozilla.org/MPL/2.0/)
[![Build Status](http://img.shields.io/travis/open-company/open-company-search.svg?style=flat)](https://travis-ci.org/open-company/open-company-search)
[![Dependencies Status](https://versions.deps.co/open-company/open-company-search/status.svg)](https://versions.deps.co/open-company/open-company-search)


## Background

> There is not a crime, there is not a dodge, there is not a trick, there is not a swindle, there is not a vice which does not live by secrecy.

> -- Joseph Pulitzer

Companies struggle to keep everyone on the same page. People are hyper-connected in the moment but still don’t know what’s happening across the company. Employees and investors, co-founders and execs, customers and community, they all want more transparency. The solution is surprisingly simple and effective - great company updates that build transparency and alignment.

With that in mind we designed the [Carrot](https://carrot.io/) software-as-a-service application, powered by the open source [OpenCompany platform](https://github.com/open-company). The product design is based on three principles:

1. It has to be easy or no one will play.
2. The "big picture" should always be visible.
3. Alignment is valuable beyond the team, too.

Carrot simplifies how key business information is shared with stakeholders to create alignment. When information about growth, finances, ownership and challenges is shared transparently, it inspires trust, new ideas and new levels of stakeholder engagement. Carrot makes it easy for founders to engage with employees and investors, creating alignment for everyone.

[Carrot](https://carrot.io/) is GitHub for the rest of your company.

Transparency expectations are changing. Organizations need to change as well if they are going to attract and retain savvy employees and investors. Just as open source changed the way we build software, transparency changes how we build successful companies with information that is open, interactive, and always accessible. Carrot turns transparency into a competitive advantage.

To get started, head to: [Carrot](https://carrot.io/)


## Overview

The OpenCompany Search Service handles searching of data in the OpenCompany system.


## Local Setup

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following local setup is **for developers** wanting to work on the OpenCompany Search Service.

Most of the dependencies are internal, meaning [Leiningen](https://github.com/technomancy/leiningen) will handle getting them for you. There are a few exceptions:

* [Java 8](http://www.oracle.com/technetwork/java/javase/downloads/index.html) - a Java 8+ JRE is needed to run Clojure
* [Leiningen](https://github.com/technomancy/leiningen) 2.5.1+ - Clojure's build and dependency management tool
* [Node.js](https://nodejs.org/en/) v6.11.2+ - JavaScript runtime

#### Java

Chances are your system already has Java 8+ installed. You can verify this with:

```console
java -version
```

If you do not have Java 8+ [download it](http://www.oracle.com/technetwork/java/javase/downloads/index.html) and follow the installation instructions.

#### Leiningen

Leiningen is easy to install:

1. Download the latest [lein script from the stable branch](https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein).
1. Place it somewhere that's on your $PATH (`env | grep PATH`). `/usr/local/bin` is a good choice if it is on your PATH.
1. Set it to be executable. `chmod 755 /usr/local/bin/lein`
1. Run it: `lein` This will finish the installation.

Then let Leiningen install the rest of the dependencies:

```console
git clone https://github.com/open-company/open-company-search.git
cd open-company-search
lein deps
```

#### Node.js

For Mac OS X, download the latest long-term support (LTS) `.pkg` installer from the [Node.js download page](https://nodejs.org/en/download/). Double click the package to run it.

For Linux, install nodejs with your distribution's preferred package manager, e.g. `sudo apt-get install build-essential nodejs nodejs-legacy npm`.

You can verify your Node.js installation by running:

```console
node -v
npm -v
```

#### Required Configuration & Secrets

An [AWS SQS queue](https://aws.amazon.com/sqs/) is used to pass messages from other OpenCompany services to the search service. Setup an SQS Queue and key/secret/endpoint access to the queue using the AWS Web Console or API.

Make sure you update the section in `project.clj` that looks like this to contain your actual JWT and AWS SQS secrets:

```clojure
:dev [:qa {
  :env ^:replace {
    :aws-access-key-id "CHANGE-ME"
    :aws-secret-access-key "CHANGE-ME"
    :endpoint "us-east-1"
    :aws-sqs-search-index-queue "https://sqs.REGION.amazonaws.com/CHANGE/ME"
    :elastic-search-endpoint 'https://ESDOMAIN.us-east-1.es.amazonaws.com/'
}
```

You can also override these settings with environmental variables in the form of `AWS_ACCESS_KEY_ID`, etc. Use environmental variables to provide production secrets when running in production.


## Usage

Prospective users of [Carrot](https://carrot.io/) should get started by going to [Carrot.io](https://carrot.io/). The following usage is **for developers** wanting to work on the OpenCompany Search Service.

**Make sure you've updated `project.clj` as described above.**

To start a development search service:

```console
lein start
```

Or to start a production search service:

```console
lein start!
```

To clean all compiled files:

```console
lein clean
```

To create a production build run:

```console
lein build
```


## Testing

Tests are run in continuous integration of the `master` and `mainline` branches on [Travis CI](https://travis-ci.org/open-company/open-company-search):

[![Build Status](http://img.shields.io/travis/open-company/open-company-search.svg?style=flat)](https://travis-ci.org/open-company/open-company-search)

To run the tests locally:

```console
lein test!
```


## Participation

Please note that this project is released with a [Contributor Code of Conduct](https://github.com/open-company/open-company-search/blob/mainline/CODE-OF-CONDUCT.md). By participating in this project you agree to abide by its terms.


## License

Distributed under the [Mozilla Public License v2.0](http://www.mozilla.org/MPL/2.0/).

Copyright © 2016-2017 OpenCompany, LLC.
