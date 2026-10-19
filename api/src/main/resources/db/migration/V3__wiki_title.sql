-- English Wikipedia article title of the event's item, for GET /events/{id}'s summary;
-- null when the item has no enwiki sitelink.
ALTER TABLE events ADD COLUMN wiki_title text;
