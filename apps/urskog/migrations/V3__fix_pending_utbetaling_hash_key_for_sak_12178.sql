UPDATE pending_utbetaling
SET hash_key = '75aa7914d7126123968b6a1382ee2fb4463a57d8192ae7977b9c075099cdfe5f'
WHERE uid = 'fe3d5d5e-15ff-4639-8ef0-053358c0772f'
  AND hash_key <> '75aa7914d7126123968b6a1382ee2fb4463a57d8192ae7977b9c075099cdfe5f'
  AND EXISTS (
      SELECT 1
      FROM oppdrag
      WHERE sak_id = '12178'
        AND hash_key = '75aa7914d7126123968b6a1382ee2fb4463a57d8192ae7977b9c075099cdfe5f'
  );
