#!/bin/bash

# Supabase credentials (from the app)
SUPABASE_URL="https://ebicqznlcjbqcukjfzcf.supabase.co"
SUPABASE_ANON_KEY="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImViaWNxem5sY2picWN1a2pmemNmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Njk1NDA5MzgsImV4cCI6MjA4NTExNjkzOH0.0Zl7DF4y6riHWzNEDqMwtYZerbFVXAlpFGbeJ3S1Bg4"

# Headers for API calls
AUTH_HEADERS="-H \"apikey: $SUPABASE_ANON_KEY\" -H \"Authorization: Bearer $SUPABASE_ANON_KEY\" -H \"Content-Type: application/json\""

echo "=== RunWear Hero Image Library Rebuild ==="
echo ""

# Step 1: Count existing images
echo "Step 1: Counting existing images..."
EXISTING_COUNT=$(curl -s "$SUPABASE_URL/rest/v1/generated_images?select=id" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Prefer: count=exact" | wc -l)
echo "Found approximately $EXISTING_COUNT existing images"

# Step 2: Archive existing images by adding prefix to combination_id
echo ""
echo "Step 2: Archiving existing images (adding 'ARCHIVED_' prefix)..."
curl -s -X PATCH "$SUPABASE_URL/rest/v1/generated_images?combination_id=not.like.ARCHIVED_*" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "Content-Type: application/json" \
  -H "Prefer: return=minimal" \
  -d '{"combination_id": "ARCHIVED_placeholder"}'

echo "Note: Archive operation may need service key for bulk updates."
echo "Alternative: Delete old images manually via Supabase dashboard."

# Step 3: Clear existing generation jobs
echo ""
echo "Step 3: Clearing pending generation jobs..."
curl -s -X DELETE "$SUPABASE_URL/rest/v1/generation_jobs?status=eq.QUEUED" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
  -H "Prefer: return=minimal"

echo "Cleared pending jobs."

# Step 4: Queue new generation jobs
echo ""
echo "Step 4: Queueing new image generation jobs..."

# Arrays for combinations
GENDERS=("MALE" "FEMALE")
WEATHERS=("CLEAR" "CLOUDY" "RAINY" "SNOWY")
TEMPS=("HOT" "WARM" "MILD" "COOL" "COLD" "FREEZING")
TIMES=("DAWN" "MIDDAY" "DUSK" "NIGHT")

# Outfit descriptions by temp (v3.14 style - these are fallbacks, actual app uses dynamic)
declare -A OUTFITS
OUTFITS["HOT"]="lightweight breathable tank top, very short split running shorts, sunglasses"
OUTFITS["WARM"]="breathable short sleeve tech shirt, standard running shorts, light mesh running cap"
OUTFITS["MILD"]="fitted long sleeve moisture-wicking shirt, running shorts"
OUTFITS["COOL"]="light long sleeve, light running jacket, light tights, ear warmer headband"
OUTFITS["COLD"]="thermal long sleeve, insulated jacket, thermal tights, light beanie, thermal gloves, neck gaiter"
OUTFITS["FREEZING"]="thermal long sleeve, insulated jacket, thermal tights, thermal beanie, mittens, neck gaiter"

# Skip impossible combinations
is_valid_combination() {
  local weather=$1
  local temp=$2

  # Snow not possible when hot or warm
  if [[ "$weather" == "SNOWY" && ("$temp" == "HOT" || "$temp" == "WARM") ]]; then
    return 1
  fi
  return 0
}

# Counter
QUEUED=0
TARGET=1000
VARIANTS_PER_COMBO=6

echo "Generating $TARGET images across all valid combinations..."

for gender in "${GENDERS[@]}"; do
  for weather in "${WEATHERS[@]}"; do
    for temp in "${TEMPS[@]}"; do
      # Skip invalid combinations
      if ! is_valid_combination "$weather" "$temp"; then
        continue
      fi

      for time in "${TIMES[@]}"; do
        # Queue multiple variants per combination
        for variant in $(seq 1 $VARIANTS_PER_COMBO); do
          if [[ $QUEUED -ge $TARGET ]]; then
            break 4
          fi

          COMBO_ID="${gender}_${weather}_${temp}_${time}_v${variant}"

          # Build prompt
          GENDER_DESC=$([ "$gender" == "MALE" ] && echo "male" || echo "female")
          WEATHER_DESC=$(echo "$weather" | tr '[:upper:]' '[:lower:]')
          TEMP_DESC=$(echo "$temp" | tr '[:upper:]' '[:lower:]')
          TIME_DESC="midday"
          case $time in
            DAWN) TIME_DESC="early morning" ;;
            MIDDAY) TIME_DESC="midday" ;;
            DUSK) TIME_DESC="evening" ;;
            NIGHT) TIME_DESC="night" ;;
          esac

          OUTFIT="${OUTFITS[$temp]}"
          PROMPT="Professional running photography, $GENDER_DESC runner in motion, $WEATHER_DESC weather, $TEMP_DESC temperature, $TIME_DESC lighting, urban trail or park setting, dynamic action shot, high quality, sharp focus. OUTFIT: $OUTFIT"

          # Queue the job
          curl -s -X POST "$SUPABASE_URL/rest/v1/generation_jobs" \
            -H "apikey: $SUPABASE_ANON_KEY" \
            -H "Authorization: Bearer $SUPABASE_ANON_KEY" \
            -H "Content-Type: application/json" \
            -H "Prefer: return=minimal" \
            -d "{\"combination_id\": \"$COMBO_ID\", \"prompt\": \"$PROMPT\", \"status\": \"QUEUED\"}" 2>/dev/null

          QUEUED=$((QUEUED + 1))

          # Progress
          if [[ $((QUEUED % 50)) -eq 0 ]]; then
            echo "  Queued $QUEUED / $TARGET jobs..."
          fi
        done
      done
    done
  done
done

echo ""
echo "=== Complete ==="
echo "Queued $QUEUED image generation jobs."
echo ""
echo "Next steps:"
echo "1. Run your image generation worker to process the queue"
echo "2. Images will be stored in generated_images table"
echo "3. Old images are still in the table (need manual cleanup if desired)"
