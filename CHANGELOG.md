## [3.0.3](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/3.0.2...3.0.3) (2026-07-02)


### Bug Fixes

* **invoker:** strip client Host header from shadow request headers ([#60](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/issues/60)) ([4451e41](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/4451e414fcc70373fcbdc3cd409b5bd05c1eb668))

## [3.0.2](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/3.0.1...3.0.2) (2026-06-23)


### Bug Fixes

* traffic shadowing does not work with failover for v2 apis ([37f6642](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/37f6642ed0b003e40b749a5e036ac53b90999525))

## [3.0.1](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/3.0.0...3.0.1) (2026-06-19)


### Bug Fixes

* prevent dynamic routing attributes from leaking into shadow context ([0a8822b](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/0a8822b9f1d8253489cbe0599efdc2d7b641cf16))

# [3.0.0](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/2.0.2...3.0.0) (2025-01-21)


### Features

* support reactive engine ([4267b79](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/4267b7976c062e20db76a98b7c9e106a6386f405))


### BREAKING CHANGES

* require at least APIM 4.6

## [2.0.2](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/2.0.1...2.0.2) (2024-12-12)


### Bug Fixes

* some handlers are mandatory before calling invoker ([ff142d6](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/ff142d6c85185bd2604c5754abd1a2e1a7fc4915))

## [2.0.1](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/2.0.0...2.0.1) (2023-07-20)


### Bug Fixes

* update policy description ([022e4e9](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/022e4e9b8ca8647fd1e83abf9635d2ceb24af98e))

# [2.0.0](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/compare/1.1.0...2.0.0) (2023-03-09)


### Bug Fixes

* fix Phase describe in the readme ([729acb0](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/729acb0d0224f795ab9f799a03be81af104ae879))


### Features

* adapt policy to APIM 3.18+ ([35130e6](https://github.com/gravitee-io/gravitee-policy-traffic-shadowing/commit/35130e64719b8d55d5953bc5eb4fd3502d05860f))


### BREAKING CHANGES

* Compatible with APIM 3.18.20, 3.19.9, 3.20.3 and upper
