# Reports-only post-incident workflow

The responder-facing **Service Review** and all star ratings remain removed. **Create Report** and the report approval workflow remain.

## Required report states

The Reports screen now exposes three responder-facing filters:

- **Pending** — not started, saved for later, or returned by admin for revision;
- **Submitted** — sent and locked while an authorized admin validates the incident and report;
- **Approved** — accepted by an authorized admin and read-only.

The backend retains legacy database values for compatibility:

| UI/API workflow status | Existing database value |
|---|---|
| `pending` | `draft` |
| `submitted` | `submitted` |
| `approved` | `verified` |
| `revision_required` | `returned` |

`workflow_status` and `status_label` are returned by the API so clients do not need to expose the legacy terms.

## Retained functions

- completed-incident list;
- Create/Continue/View Report;
- server-backed report save and submission;
- admin approve/return workflow;
- reviewer notes on returned reports;
- completion evidence preview and PDF export;
- equipment and supply requests.

## Removed functions

- Service Review composer and confirmation dialog;
- response-time, communication, and professionalism star ratings;
- rating averages/analytics;
- service-review quick templates;
- `get-pending-review-incidents.php`;
- `get-my-incident-reviews.php`;
- `submit-incident-review.php`.

The navigation route remains `reviews_feedback` internally to preserve existing deep links and saved navigation state. The visible destination is **Reports**.
