# Railwize diagrams

Prepared from the local implementation on 13 October 2026.

Files are numbered in the requested order. Each diagram is available as a vector PDF, editable SVG, and PNG image. Open the PDFs at a comfortable zoom; for printing use Fit to page, preferably A3 for the detailed ER and architecture views.

1. Activity: selected booking lifecycle, including search, authentication, passenger selection, server quote, two-minute hold, verified test payment, paid WL, promotion, cancellation and refund updates. Background processing and retry states are condensed into annotated activities. CNF/WL is per booking, not per passenger; there is no RAC.
2. Class: selected reservation-service classes and collaborators. This is a focused implementation diagram, not an inventory of every application class. Method arguments and less relevant fields are omitted. Composition connects Reservation to Passenger; dashed arrows show dependencies.
3. ER: core entities across all eight service-owned schemas. Selected Java property names are used alongside explicit table/column names. Solid lines identify mapped database relationships; dashed lines show identifier-based relationships, including those within one schema. Dashed cross-service links must not be interpreted as database foreign keys. Supporting outbox, audit and configuration tables omitted from the view are listed in its footer.
4. Use case: visitor, passenger and administrator capabilities within the application boundary. Actor association lines are bundled for readability; these lines do not describe sequence. Provider relationships are identified by name below the boundary. Public search/help is accessible independently of private booking operations.
5. Architecture: all 12 runnable services, the Angular client, service-owned schemas and external infrastructure. Selected routing/internal-call arrows are shown. Configuration and discovery apply across participating services. The notes identify additional service/provider relationships; return traffic and every endpoint are intentionally omitted.

Important: all financial transactions use Razorpay test mode. The platform uses its own train catalogue and inventory, not live Indian Railways reservations. Connecting legs are independently booked and paid; nearby stations remain visible as the actual ticket endpoints.

Evidence: backend/README.md, EVALUATION-GUIDE.md, current service/entity/repository source files, and config-server's api-gateway.properties. This deliverable does not alter the application or database.
