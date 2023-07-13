# Test Assignment

This application's purpose is to: having a list of numbers and an arbitrary number called `target`, 
find two numbers (and their indexes), which sum will be equal to `target`.

This application consists of 2 services. 

## target-finder

The `target-finder` is responsible for finding those 2 numbers and their indexes.
It needs an array of numbers and the target to find those numbers.

You can query this service like so:
```
curl -X POST 'http://localhost:8080/target' -H 'Content-Type: application/json' --data '{ "data": [ 1, 2, 3, -4 ], "target": -2 }'
```
Both `data` and `target` must be present.

This service can handle a few faulty scenarios:
- It can handle cases when the data does not contain a pair of appropriate numbers 
(the second service sometimes can yield a default `target`, which will not always satisfy all conditions).
```
{
    "CouldNotFindAppropriateValues": {}
}
```
- It handles cases when there is not enough data because there must be at least 2 numbers.
```
{
    "NotEnoughData": {}
}
```
The error code will be 400 is such cases.

When you try to access a resource, which does not exist you will get 404.

## target-router

This service is responsible for fetching `target` from `https://httpbin.org` and 
forwarding it to `target-finder`. It follows similar patter as `target-finder`, 
it accepts the object (however, this time `target` is optional),
but uses a different endpoint and port. Besides, it is rate limited.

You can query this service like so:
```
curl -X POST 'http://localhost:4040/find' -H 'Content-Type: application/json' --data '{ "data": [ 1, 2, 3, -4 ], "target": -2 }'
```
You can exclude `target`. In this case, it will be fetched from `https://httpbin.org/get`.
In case you provide `target`, it will use `https://httpbin.org/get?target=[target]`.
In case it encounters any error, it will use the default value, which can be configured via an env called `TARGET`.

This service forwards all request to `target-finder` and preserves the response, meaning you may encounter errors specified for `target-finder`.

This endpoint is rate limited. It uses a token bucket rate limiter to achieve it.
You need to specify the number of tokens via `TOKENS` env and the number of requests per minute via `REQUESTS` env.
Each time the client makes a request, it will need to have at least one available token. 
Otherwise, the request will be rate limited and rejected.
The tokens get recovered in such a rate that you will be able to make `REQUESTS` per minute.
This token behavior allows to handle bursts of request gracefully.


Each request will be enriched with `X-Rate-Limit-Limit`, `X-Rate-Limit-Remaining` and `X-Rate-Limit-Reset`.
`X-Rate-Limit-Limit` is the maximum number of tokens, `X-Rate-Limit-Remaining` is their remaining number, and
`X-Rate-Limit-Reset` is a timestamp when all used tokens (`X-Rate-Limit-Limit` - `X-Rate-Limit-Remaining`) will be recovered if no one uses the API.
You can view logs to monitor the recovery of the tokens.
You can 


Besides, you can disable `target-finder` with `DEBUG_RATE_LIMITS` to test these headers.
With this env enabled all requests will be automatically rate limited.


## Deployment

To build Docker images locally, run:
```shell
sbt docker:publishLocal
```
After this, 2 Docker images will be created, `target-finder` and `target-router`.
Then, you will simply need to run:
```shell
docker-compose up
```
You may want to look into the `docker-compose.yaml` to check and modify envs.

## Technology stack

ZIO, ZIO-HTTP, Circe
