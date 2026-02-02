// generate-thumbnails.js
// Generates AI clothing thumbnails and uploads to Supabase
// Usage: npm run generate

import Replicate from 'replicate';
import { createClient } from '@supabase/supabase-js';
import fs from 'fs';

// Configuration
const REPLICATE_API_TOKEN = process.env.REPLICATE_API_TOKEN;
const SUPABASE_URL = process.env.SUPABASE_URL || 'https://ebicqznlcjbqcukjfzcf.supabase.co';
const SUPABASE_SERVICE_KEY = process.env.SUPABASE_SERVICE_KEY;

const replicate = new Replicate({ auth: REPLICATE_API_TOKEN });
const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

// Parse command line args for gender filter
const args = process.argv.slice(2);
const genderArg = args.find(a => a.startsWith('--gender='));
const filterGender = genderArg ? genderArg.split('=')[1] : null;

// ============================================================
// CLOTHING ITEMS - Matching PWA ClothingItems keys
// ============================================================

const BASE_ITEMS = [
  // Tops - Base Layer
  { key: 'tank-top', pwaKey: 'TANK_TOP',
    male: 'mens running tank top singlet, athletic cut, breathable mesh fabric, sleeveless with wide armholes',
    female: 'womens running tank top, racerback design, fitted athletic cut, breathable mesh fabric, sleeveless' },
  { key: 'short-sleeve', pwaKey: 'SHORT_SLEEVE',
    male: 'mens technical running t-shirt, relaxed athletic fit, moisture-wicking fabric, crew neck',
    female: 'womens technical running t-shirt, slim fitted cut, moisture-wicking fabric, crew neck or v-neck' },
  { key: 'long-sleeve-light', pwaKey: 'LONG_SLEEVE_LIGHT',
    male: 'mens lightweight long sleeve running shirt, athletic fit, quarter zip neck, thumbholes at cuffs',
    female: 'womens lightweight long sleeve running shirt, slim fit, quarter zip neck, thumbholes at cuffs' },
  { key: 'thermal-long-sleeve', pwaKey: 'LONG_SLEEVE_THERMAL',
    male: 'mens thermal compression base layer shirt, fitted athletic cut, brushed fleece interior, mock neck',
    female: 'womens thermal compression base layer shirt, slim fitted cut, brushed fleece interior, mock neck' },

  // Tops - Outer Layer
  { key: 'light-vest', pwaKey: 'LIGHT_VEST',
    male: 'mens lightweight running vest, wind-resistant front panel, mesh back, athletic fit',
    female: 'womens lightweight running vest, wind-resistant, tapered feminine fit, reflective accents' },
  { key: 'light-jacket', pwaKey: 'LIGHT_JACKET',
    male: 'mens lightweight packable running windbreaker jacket, athletic fit, thin nylon shell, zippered pockets',
    female: 'womens lightweight packable running windbreaker jacket, tapered waist, thin nylon shell, zippered pockets' },
  { key: 'insulated-jacket', pwaKey: 'INSULATED_JACKET',
    male: 'mens insulated puffy running jacket, athletic fit, lightweight quilted design, high collar',
    female: 'womens insulated puffy running jacket, feminine fit with waist shaping, lightweight quilted, high collar' },
  { key: 'rain-jacket', pwaKey: 'RAIN_JACKET',
    male: 'mens waterproof running rain jacket, athletic fit, sealed seams, hood with brim, reflective accents',
    female: 'womens waterproof running rain jacket, tapered fit, sealed seams, hood with brim, reflective accents' },
  { key: 'windbreaker', pwaKey: 'WINDBREAKER',
    male: 'mens running windbreaker jacket, wind-resistant fabric, athletic fit, zippered front',
    female: 'womens running windbreaker jacket, wind-resistant fabric, fitted silhouette, zippered front' },

  // Bottoms
  { key: 'short-shorts', pwaKey: 'SHORT_SHORTS',
    male: 'mens running split shorts 3-inch inseam, lightweight with built-in brief liner, side vents',
    female: 'womens running shorts 3-inch inseam, built-in brief, flattering fit, side slits' },
  { key: 'running-shorts', pwaKey: 'SHORTS',
    male: 'mens running shorts 5-inch inseam, moisture-wicking fabric, elastic waistband with drawstring',
    female: 'womens running shorts 4-inch inseam, moisture-wicking fabric, wide waistband, inner brief' },
  { key: 'light-tights', pwaKey: 'LIGHT_TIGHTS',
    male: 'mens full-length running tights, lightweight compression, reflective details, fitted',
    female: 'womens full-length running leggings, high waist, lightweight compression, reflective logo' },
  { key: 'thermal-tights', pwaKey: 'THERMAL_TIGHTS',
    male: 'mens thermal running tights, brushed fleece interior, full-length, athletic compression fit',
    female: 'womens thermal running leggings, high waist, brushed fleece interior, full-length compression' },

  // Head
  { key: 'running-cap', pwaKey: 'BASEBALL_CAP',
    male: 'mens lightweight running cap, curved brim, mesh back panels, moisture-wicking sweatband',
    female: 'womens lightweight running cap, curved brim, ponytail hole in back, moisture-wicking' },
  { key: 'visor', pwaKey: 'VISOR',
    male: 'mens running sun visor, open top design, curved brim, adjustable back strap',
    female: 'womens running sun visor, open top for ponytail, curved brim, adjustable velcro back' },
  { key: 'headband', pwaKey: 'HEADBAND',
    male: 'mens athletic running headband ear warmer, wide stretchy fabric, sweat-wicking, neutral color',
    female: 'womens athletic running headband ear warmer, wide stretchy fabric, sweat-wicking, stylish design' },
  { key: 'light-beanie', pwaKey: 'LIGHT_BEANIE',
    male: 'mens running beanie, thin thermal knit, fitted design, covers ears fully',
    female: 'womens running beanie, thin thermal knit, ponytail hole option, covers ears' },
  { key: 'thermal-beanie', pwaKey: 'THERMAL_BEANIE',
    male: 'mens thick thermal running beanie, insulated fleece lined, fitted design',
    female: 'womens thick thermal running beanie, insulated fleece lined, ponytail compatible' },
  { key: 'balaclava', pwaKey: 'BALACLAVA',
    male: 'mens running balaclava face mask, full head and neck coverage, breathing hole, thermal',
    female: 'womens running balaclava face mask, full head and neck coverage, ponytail compatible, thermal' },

  // Hands
  { key: 'light-gloves', pwaKey: 'LIGHT_GLOVES',
    male: 'mens lightweight running gloves, touchscreen fingertips, thin breathable fabric, size large',
    female: 'womens lightweight running gloves, touchscreen fingertips, thin breathable fabric, slim fit' },
  { key: 'thermal-gloves', pwaKey: 'THERMAL_GLOVES',
    male: 'mens insulated running gloves, fleece lined, wind-resistant shell, reflective accents',
    female: 'womens insulated running gloves, fleece lined, wind-resistant shell, slim feminine fit' },
  { key: 'mittens', pwaKey: 'MITTENS',
    male: 'mens running mittens, convertible fold-back finger cover, insulated windproof shell',
    female: 'womens running mittens, convertible fold-back finger cover, insulated, slim fit' },

  // Accessories
  { key: 'sunglasses', pwaKey: 'SUNGLASSES',
    male: 'mens sport running sunglasses, wraparound frame, polarized lenses, bold angular design',
    female: 'womens sport running sunglasses, wraparound frame, polarized lenses, sleeker profile' },
  { key: 'reflective-gear', pwaKey: 'REFLECTIVE_GEAR',
    male: 'mens high-visibility reflective running vest, neon with reflective strips, mesh, athletic fit',
    female: 'womens high-visibility reflective running vest, neon with reflective strips, tapered fit' },
  { key: 'neck-gaiter', pwaKey: 'NECK_GAITER',
    male: 'mens running neck gaiter buff, tubular design, moisture-wicking fabric, neutral color',
    female: 'womens running neck gaiter buff, tubular design, moisture-wicking fabric, stylish pattern' },
  { key: 'sunscreen', pwaKey: 'SUNSCREEN',
    male: 'sport sunscreen stick and bottle, sweat-resistant SPF 50, travel size, unisex product',
    female: 'sport sunscreen stick and bottle, sweat-resistant SPF 50, travel size, unisex product' },
];

// Generate the full gendered item list
const GENDERS = filterGender ? [filterGender] : ['male', 'female'];
const CLOTHING_ITEMS = BASE_ITEMS.flatMap(item =>
  GENDERS.map(gender => ({
    key: `${item.key}-${gender}`,
    pwaKey: item.pwaKey,
    name: `${item.key} (${gender})`,
    prompt: item[gender],
    gender: gender
  }))
);

// ============================================================
// PROMPT TEMPLATE
// ============================================================
const BASE_PROMPT = (itemPrompt) => `
Product photography of ${itemPrompt},
dark gray or charcoal colored fabric,
athletic running gear isolated on pure black background,
professional studio lighting with soft key light from upper left,
slight 15-degree rotation for depth,
no mannequin no model no human,
floating product centered in frame,
high-end athletic sportswear catalog style,
sharp focus crisp details,
8k product render
`.trim().replace(/\n/g, ' ');

// ============================================================
// GENERATION FUNCTION
// ============================================================
async function generateThumbnail(item) {
  console.log(`\n  Generating: ${item.name} (${item.key})`);

  const prompt = BASE_PROMPT(item.prompt);

  try {
    // Use Flux Dev for higher quality product shots
    const output = await replicate.run(
      "black-forest-labs/flux-dev",
      {
        input: {
          prompt: prompt,
          num_outputs: 1,
          aspect_ratio: "1:1",  // Square for thumbnails
          output_format: "webp",
          output_quality: 90,
          num_inference_steps: 28,  // Flux Dev default for quality
          guidance_scale: 3.5,      // Recommended for Flux Dev
        }
      }
    );

    // Flux returns array of URLs
    const imageUrl = Array.isArray(output) ? output[0] : output;

    if (!imageUrl) {
      throw new Error('No image URL returned');
    }

    console.log(`  Generated: ${imageUrl.substring(0, 60)}...`);
    return { ...item, generatedUrl: imageUrl };

  } catch (error) {
    console.error(`  Failed: ${error.message}`);
    return { ...item, error: error.message };
  }
}

// ============================================================
// UPLOAD TO SUPABASE
// ============================================================
async function uploadToSupabase(item) {
  if (!item.generatedUrl) {
    console.log(`  Skipping upload for ${item.key} (no URL)`);
    return item;
  }

  console.log(`  Uploading ${item.key} to Supabase...`);

  try {
    // Fetch the image
    const response = await fetch(item.generatedUrl);
    const arrayBuffer = await response.arrayBuffer();
    const buffer = Buffer.from(arrayBuffer);

    // Upload to Supabase Storage
    const filename = `${item.key}.webp`;
    const { data, error } = await supabase.storage
      .from('clothing-thumbnails')
      .upload(filename, buffer, {
        contentType: 'image/webp',
        upsert: true  // Overwrite if exists
      });

    if (error) throw error;

    // Get public URL
    const { data: urlData } = supabase.storage
      .from('clothing-thumbnails')
      .getPublicUrl(filename);

    console.log(`  Uploaded: ${filename}`);
    return { ...item, supabaseUrl: urlData.publicUrl };

  } catch (error) {
    console.error(`  Upload failed: ${error.message}`);
    return { ...item, uploadError: error.message };
  }
}

// ============================================================
// MAIN EXECUTION
// ============================================================
async function main() {
  console.log('RunWear Clothing Thumbnail Generator');
  console.log('====================================\n');
  console.log(`Items to generate: ${CLOTHING_ITEMS.length}`);
  if (filterGender) {
    console.log(`Filtered to gender: ${filterGender}`);
  }

  // Check environment
  if (!REPLICATE_API_TOKEN) {
    console.error('\nMissing REPLICATE_API_TOKEN!');
    console.error('Create a .env file with:');
    console.error('  REPLICATE_API_TOKEN=r8_your_token_here');
    console.error('  SUPABASE_SERVICE_KEY=your_service_key_here');
    process.exit(1);
  }

  if (!SUPABASE_SERVICE_KEY) {
    console.error('\nMissing SUPABASE_SERVICE_KEY!');
    console.error('Get it from Supabase Dashboard > Settings > API > service_role key');
    process.exit(1);
  }

  const results = [];
  let completed = 0;

  // Process items with rate limiting
  for (const item of CLOTHING_ITEMS) {
    completed++;
    console.log(`\n[${completed}/${CLOTHING_ITEMS.length}] Processing ${item.key}...`);

    // Generate
    const generated = await generateThumbnail(item);

    // Upload
    const uploaded = await uploadToSupabase(generated);
    results.push(uploaded);

    // Rate limit: wait between generations
    if (completed < CLOTHING_ITEMS.length) {
      console.log('  Waiting 12s before next generation...');
      await new Promise(resolve => setTimeout(resolve, 12000));
    }
  }

  // Summary
  console.log('\n====================================');
  console.log('GENERATION SUMMARY');
  console.log('====================================\n');

  const successful = results.filter(r => r.supabaseUrl);
  const failed = results.filter(r => r.error || r.uploadError);

  console.log(`Successful: ${successful.length}/${CLOTHING_ITEMS.length}`);
  console.log(`Failed: ${failed.length}/${CLOTHING_ITEMS.length}`);

  if (failed.length > 0) {
    console.log('\nFailed items:');
    failed.forEach(f => console.log(`  - ${f.key}: ${f.error || f.uploadError}`));
  }

  // Output URL mapping for PWA integration
  console.log('\n====================================');
  console.log('URL MAPPING');
  console.log('====================================\n');

  // Group by pwaKey for easier integration
  const mapping = {};
  successful.forEach(item => {
    if (!mapping[item.pwaKey]) {
      mapping[item.pwaKey] = {};
    }
    mapping[item.pwaKey][item.gender] = item.supabaseUrl;
  });

  console.log('const CLOTHING_THUMBNAILS = {');
  Object.entries(mapping).forEach(([pwaKey, genders]) => {
    console.log(`  ${pwaKey}: {`);
    Object.entries(genders).forEach(([gender, url]) => {
      console.log(`    ${gender}: '${url}',`);
    });
    console.log('  },');
  });
  console.log('};');

  // Save to file
  fs.writeFileSync(
    'thumbnail-urls.json',
    JSON.stringify(mapping, null, 2)
  );
  console.log('\nSaved URL mapping to thumbnail-urls.json');

  // Estimate cost
  const cost = successful.length * 0.025;
  console.log(`\nEstimated cost: $${cost.toFixed(2)} (${successful.length} images x $0.025)`);
}

main().catch(console.error);
