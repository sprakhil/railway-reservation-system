-- Only for installations using ddl-auto=validate/none; default update creates these columns.
ALTER TABLE stations ADD COLUMN latitude DOUBLE NULL, ADD COLUMN longitude DOUBLE NULL;
-- Populate verified station coordinates through the admin station editor. No guessed locations are seeded.
