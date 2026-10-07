-- Use one normalization rule for both the current menu and saved menu names.
CREATE FUNCTION welstory_normalize_menu_name(menu_name TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE PARALLEL SAFE AS $$
    WITH canonical AS (
        SELECT lower(normalize(COALESCE(menu_name, ''), NFKC)) AS name
    ), without_tags AS (
        SELECT name, regexp_replace(name, '\[[^\[\]]*\]', '', 'g') AS untagged
        FROM canonical
    ), compact AS (
        -- A bracket-only title such as [라면 14종 중 택1] is a menu, not a tag.
        SELECT regexp_replace(
            CASE WHEN regexp_replace(untagged, '[^[:alnum:]]', '', 'g') <> ''
                 THEN untagged ELSE name END,
            '[^[:alnum:]]', '', 'g') AS name
        FROM without_tags
    )
    SELECT name FROM compact;
$$;
