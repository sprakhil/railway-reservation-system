> Current additions and API changes: see [README.md](../README.md). Catalogue/history lists are now paginated; paid WL, sessions/logout, admin invitations and demo PDFs are included.

# Train catalog setup and API

All amounts are configurable sample fares in INR. They are project data, not official railway tariffs.

Existing endpoints remain available: public `GET /api/trains`, `GET /api/trains/{trainNumber}`, and station reads; administrator-only `POST /api/trains`, `PUT /api/trains/{trainNumber}` and station mutations.

Train requests/responses now include `operatingDays` (uppercase Java day names) and `classConfigurations`. Each configuration contains `seatClass`, `coachCount`, `seatsPerCoach`, `baseFare`, `ratePerKm`, and `minimumFare`. Configure every declared seat class together. Valid classes are `CLASS_1A`, `CLASS_2A`, `CLASS_3A`, `CLASS_3E`, `EC`, `SL`, `CLASS_2S`, and `UR`.

```json
{
  "trainNumber": "12045",
  "trainName": "Demo Express",
  "category": "EXPRESS",
  "sourceStationId": 1,
  "destinationStationId": 2,
  "seatClasses": ["SL"],
  "operatingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
  "classConfigurations": [
    {"seatClass": "SL", "coachCount": 2, "seatsPerCoach": 72, "baseFare": 20.00, "ratePerKm": 0.50, "minimumFare": 40.00}
  ]
}
```

Class capacity is `coachCount * seatsPerCoach`. Each class permits 1–30 coaches and 1–100 seats per coach. Monetary fields are nonnegative with at most two decimal places. The reservation service calculates and snapshots the fare; clients cannot supply payment totals.

Existing trains with no class configurations or operating days are returned with empty arrays. They have no invented inventory and remain unbookable until configured by an administrator. Train numbers, source/destination IDs, and station codes cannot change. Once inventory is configured, class membership, capacities and operating days are immutable to protect active reservations. Train names, categories, station descriptive fields and fares can still be edited. Use a new train number for another operating pattern or capacity.

Before deployment, back up the database and run `src/main/resources/db/manual-migrations/001_catalog_inventory.sql` against the existing train database. It adds empty configuration tables without guessing existing inventory. Hibernate `ddl-auto=update` can create these tables for a fresh development database, but the SQL is supplied for deliberate upgrades. Test configuration uses isolated H2 and disables Config Server/Eureka; `mvn test` needs no external services.

HTTP errors are 400 for invalid requests, 404 for missing resources, and 409 for duplicate identifiers or attempts to change immutable data. Concurrent updates lock the train row, and database uniqueness protects duplicate train/station creation.
