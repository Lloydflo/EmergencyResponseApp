# Alternative Route Integration Contract

Version 17.5 keeps the first route locked. A traffic request never removes or
recalculates that route while the responder waits. The Android client activates
an alternative only after the server returns a valid result for the exact
pending request ID.

## 1. Submit a request

`POST api/api_app/request-alternative-route.php`

JSON body:

```json
{
  "incident_id": "42",
  "assignment_id": "9001",
  "responder_id": 77,
  "start_lat": 14.5995,
  "start_lng": 120.9842,
  "destination_lat": 14.6091,
  "destination_lng": 121.0223
}
```

Accepted response:

```json
{
  "success": true,
  "request_id": 12345,
  "status": "pending",
  "message": "Route request queued"
}
```

The server must authenticate the responder, verify the assignment/incident,
generate a unique positive request ID, and preserve the submitted start and
destination for later validation.

## 2. Poll the result

`GET api/api_app/get-alternative-route-status.php?request_id=12345&responder_id=77`

While processing, return the same `request_id`, `success: true`, and status
`pending` or `processing`.

Ready response:

```json
{
  "success": true,
  "request_id": 12345,
  "status": "ready",
  "points": [
    { "sequence": 0, "lat": 14.5995, "lng": 120.9842 },
    { "sequence": 1, "lat": 14.6032, "lng": 121.0011 },
    { "sequence": 2, "lat": 14.6091, "lng": 121.0223 }
  ],
  "distance_m": 6200.0,
  "duration_s": 840.0,
  "message": "Approved route ready"
}
```

Use `failed` when no safe route can be supplied and `cancelled` when the request
was withdrawn. Include a responder-safe `message` for either state.

## Client acceptance rules

The client keeps the current route unless all checks pass:

- `success` is true and `request_id` exactly matches the pending request;
- the result arrives within two minutes of the original request;
- there are 2–20,000 finite, in-range coordinates;
- the first and last route points are within 60 metres of the submitted start
  and the locked incident destination (reverse order is accepted and oriented);
- the calculated route length is positive and no more than the greater of five
  times the direct distance or 10 km; and
- the same responder, assignment, and active route session still own the
  request when the alternative is committed.

Acceptance is atomic on the device: the approved route, selected route mode,
step reset, and pending-request cleanup are saved together. A late response for
an older request cannot overwrite a newer request. **Restore Original Route**
uses the stored first route and does not call the routing service again.

## Server-side requirements

- Enforce responder authentication and assignment authorization on both
  endpoints; never rely on the numeric `responder_id` alone.
- Bind every result to its request, responder, assignment, incident, start, and
  destination.
- Return immutable completed geometry for a request. Do not recycle IDs.
- Apply an expiry at or before two minutes and reject cross-responder reads.
- Use HTTPS and parameterized database queries, and keep an audit timestamp for
  request creation, approval/failure, and the approving service/operator.

