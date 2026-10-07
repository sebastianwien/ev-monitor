import { describe, it, expect } from 'vitest'
import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { MODEL_IMAGES, officialModelImageUrl, officialModelThumbUrl } from '../modelImages'

const IMAGE_DIR = resolve(__dirname, '../../../public/model-images')

describe('modelImages', () => {
  it('resolves the hero image and the small variant for the ranking row', () => {
    expect(officialModelImageUrl('MODEL_3')).toBe('/model-images/MODEL_3.jpg')
    expect(officialModelThumbUrl('MODEL_3')).toBe('/model-images/thumbs/MODEL_3.jpg')
    expect(officialModelImageUrl('UNKNOWN')).toBeNull()
    expect(officialModelThumbUrl('UNKNOWN')).toBeNull()
  })

  it('ships both files for every entry (run scripts/model-image-thumbs.py after adding an image)', () => {
    const missing = Object.values(MODEL_IMAGES)
      .flatMap(e => [e.file, `thumbs/${e.file}`])
      .filter(f => !existsSync(resolve(IMAGE_DIR, f)))
    expect(missing).toEqual([])
  })
})
