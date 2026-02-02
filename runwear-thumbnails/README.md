# RunWear Clothing Thumbnails Generator

Generates AI clothing thumbnails using Replicate's Flux Dev model and uploads to Supabase Storage.

## Setup

### 1. Create Supabase Storage Bucket

Go to your Supabase Dashboard > Storage > New Bucket:
- Name: `clothing-thumbnails`
- Public: ON (toggle enabled)
- File size limit: 1MB
- Allowed MIME types: `image/webp, image/png`

Or via SQL Editor:
```sql
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
  'clothing-thumbnails',
  'clothing-thumbnails',
  true,
  1048576,
  ARRAY['image/webp', 'image/png']
)
ON CONFLICT (id) DO NOTHING;
```

### 2. Configure Environment

Copy `.env.example` to `.env` and fill in your keys:

```bash
cp .env.example .env
```

Edit `.env`:
- `REPLICATE_API_TOKEN`: Get from https://replicate.com/account/api-tokens
- `SUPABASE_SERVICE_KEY`: Get from Supabase Dashboard > Settings > API > service_role (secret key)

### 3. Install Dependencies

```bash
npm install
```

### 4. Run Generation

Generate all 52 thumbnails (26 items × 2 genders):

```bash
npm run generate
```

Or generate only one gender:

```bash
npm run generate:male
npm run generate:female
```

## Output

- Images uploaded to `clothing-thumbnails` bucket in Supabase
- `thumbnail-urls.json` created with URL mapping
- Console outputs the mapping in JavaScript format

## Cost

- ~$0.025 per image (Flux Dev)
- 52 images = ~$1.30 total
- Generation time: ~12-15 minutes

## Troubleshooting

### Rate limit errors
Increase the delay between generations (edit `setTimeout` in script)

### Upload fails
- Ensure bucket exists and is public
- Check that SUPABASE_SERVICE_KEY is the service_role key (not anon key)

### Images not loading in PWA
- Verify bucket is public in Supabase Dashboard
- Check browser Network tab for 404 errors
- Ensure filenames match expected pattern: `{item-key}-{gender}.webp`
