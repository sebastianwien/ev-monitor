/**
 * Curated official model images for the public model cards (/modelle).
 * Keyed by the CarModel enum name (same value as TopModelPreview.model).
 * Images live in frontend/public/model-images/ and ship with the app.
 * LICENSING: every entry carries verbatim attribution from the source (Wikimedia Commons).
 * Only free licences (CC / public domain). Attribution is rendered on the card hero.
 */
export interface ModelImageAttribution {
  author: string
  license: string
  licenseUrl: string
  sourceUrl: string
}

export interface ModelImageEntry extends ModelImageAttribution {
  file: string
}

const MODEL_IMAGE_BASE = '/model-images'

export const MODEL_IMAGES: Record<string, ModelImageEntry> = {
  CORSA_E: { file: 'CORSA_E.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Opel_Corsa-e_at_IAA_2019_IMG_0738.jpg' },
  CUPRA_BORN: { file: 'CUPRA_BORN.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Cupra_Born_%E2%80%93_f_03042026.jpg' },
  CUPRA_TAVASCAN: { file: 'CUPRA_TAVASCAN.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Cupra_Tavascan_Endurance_%E2%80%93_f_18042026.jpg' },
  DACIA_SPRING: { file: 'DACIA_SPRING.jpg', author: 'Autosdeprimera', license: 'CC BY 3.0', licenseUrl: 'https://creativecommons.org/licenses/by/3.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2023_Renault_Kwid_Iconic_(Colombia)_front_view_01.png' },
  ELROQ: { file: 'ELROQ.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:%C5%A0koda_Elroq_DSC_9260.jpg' },
  ENYAQ: { file: 'ENYAQ.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:%C5%A0koda_Enyaq_IMG_1190.jpg' },
  ENYAQ_COUPE: { file: 'ENYAQ_COUPE.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:%C5%A0koda_Enyaq_IMG_1190.jpg' },
  EQA: { file: 'EQA.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Mercedes-Benz_H243_IMG_5876.jpg' },
  EQB: { file: 'EQB.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Mercedes-Benz_X243_300_1X7A0422.jpg' },
  EQE: { file: 'EQE.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Mercedes-Benz_V295_350%2B_Classic-Days_2022_DSC_0018.jpg' },
  EV_3: { file: 'EV_3.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Kia_EV3_GT-Line_Automesse_Ludwigsburg_2025_DSC_2635_(cropped).jpg' },
  EV_6: { file: 'EV_6.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2021_Kia_EV6_GT-Line_S.jpg' },
  EV_9: { file: 'EV_9.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Kia_EV9_1X7A2472.jpg' },
  EXPLORER_EV: { file: 'EXPLORER_EV.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Ford_Explorer_EV_IMG_2120.jpg' },
  E_208: { file: 'E_208.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Peugeot_e-208_facelift_1X7A2502.jpg' },
  E_SOUL: { file: 'E_SOUL.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2020_Kia_Soul_First_Edition_EV_Front.jpg' },
  E_UP: { file: 'E_UP.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2013_Volkswagen_Take_UP!_1.0.jpg' },
  FIAT_500E: { file: 'FIAT_500E.jpg', author: 'Daniel Przygoda', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Fiat-500-vorne2.jpg' },
  I3: { file: 'I3.jpg', author: 'MrChia SG', license: 'CC BY 4.0', licenseUrl: 'https://creativecommons.org/licenses/by/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Bmw_i3_2027.png' },
  I4: { file: 'I4.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:BMW_i4_IMG_6695.jpg' },
  I5: { file: 'I5.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:BMW_G60_520i_1X7A2443.jpg' },
  ID_3: { file: 'ID_3.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2020_Volkswagen_ID.3_1st_Front.jpg' },
  ID_4: { file: 'ID_4.jpg', author: 'LuvsMG481', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2025_Volkswagen_ID4_Pro_Redspot_front.jpg' },
  ID_5: { file: 'ID_5.jpg', author: 'LuvsMG481', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2025_Volkswagen_ID4_Pro_Redspot_front.jpg' },
  ID_7: { file: 'ID_7.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Volkswagen_ID.7_DSC_7879_(cropped).jpg' },
  ID_BUZZ: { file: 'ID_BUZZ.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Volkswagen_ID._Buzz_1X7A6263.jpg' },
  IONIQ_5: { file: 'IONIQ_5.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Hyundai_Ioniq_5_AWD_Techniq-Paket_%E2%80%93_f_31122024.jpg' },
  IONIQ_6: { file: 'IONIQ_6.jpg', author: 'Kevauto', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2023_Hyundai_Ioniq_6_Limited,_front_4.27.23.jpg' },
  IONIQ_ELECTRIC: { file: 'IONIQ_ELECTRIC.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Hyundai_IONIQ_electric_Premium_%E2%80%93_Frontansicht,_7._Mai_2017,_D%C3%BCsseldorf.jpg' },
  IX1: { file: 'IX1.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2022_BMW_X1_sDrive18d_M_Sport_MHEV_Automatic_2.0.jpg' },
  KONA_ELECTRIC: { file: 'KONA_ELECTRIC.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Hyundai_Kona_N_Line_(SX2)_DSC_8250.jpg' },
  LEAF: { file: 'LEAF.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Nissan_Leaf_(ZE2)_autoMOBIL_T%C3%BCbingen_2025_DSC_2752.jpg' },
  MEGANE_E_TECH: { file: 'MEGANE_E_TECH.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Renault_M%C3%A9gane_E-Tech_IMG_4064.jpg' },
  MINI_COOPER_SE_F56: { file: 'MINI_COOPER_SE_F56.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Mini_Hatch_(F56)_Electric_IMG_2679.jpg' },
  MODEL_3: { file: 'MODEL_3.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Tesla_Model_3_(2023)_Autofr%C3%BChling_Ulm_IMG_9282.jpg' },
  MODEL_S: { file: 'MODEL_S.jpg', author: 'Peteratkins', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Tesla_Model_S_(Facelift_ab_04-2016)_(cropped).jpg' },
  MODEL_X: { file: 'MODEL_X.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2017_Tesla_Model_X_100D_Front.jpg' },
  MODEL_Y: { file: 'MODEL_Y.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Tesla_Model_Y_Premium_(Facelift)_%E2%80%93_f_05052026.jpg' },
  POLESTAR_2: { file: 'POLESTAR_2.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Polestar_2_%E2%80%93_f_02042021.jpg' },
  POLESTAR_3: { file: 'POLESTAR_3.jpg', author: 'Alexander-93', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Polestar_3_Auto_Zuerich_2023_1X7A1307.jpg' },
  POLESTAR_4: { file: 'POLESTAR_4.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Polestar_4_DSC_8232_(cropped_2).jpg' },
  Q4_E_TRON: { file: 'Q4_E_TRON.jpg', author: 'Vauxford', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2021_Audi_Q4_e-tron_Sport_35.jpg' },
  RENAULT_5: { file: 'RENAULT_5.jpg', author: 'Alexander Migl', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Renault_5_E-Tech_Electric_DSC_7279.jpg' },
  XPENG_G6: { file: 'XPENG_G6.jpg', author: 'JustAnotherCarDesigner', license: 'CC0', licenseUrl: 'http://creativecommons.org/publicdomain/zero/1.0/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:XPeng_G6_018_(cropped).jpg' },
  XPENG_G9: { file: 'XPENG_G9.jpg', author: 'User3204', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0', sourceUrl: 'https://commons.wikimedia.org/wiki/File:2022_XPeng_G9_(front).jpg' },
  XPENG_P7: { file: 'XPENG_P7.jpg', author: 'Poprace', license: 'CC0', licenseUrl: 'http://creativecommons.org/publicdomain/zero/1.0/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:XPeng_P7_II_MY2025_IMG03.jpg' },
  ZOE: { file: 'ZOE.jpg', author: '© M 93', license: 'CC BY-SA 3.0 de', licenseUrl: 'https://creativecommons.org/licenses/by-sa/3.0/de/deed.en', sourceUrl: 'https://commons.wikimedia.org/wiki/File:Renault_Zoe_R110_Z.E._50_Experience_(Facelift)_%E2%80%93_f_22112020.jpg' },
}

export function officialModelImageUrl(model: string): string | null {
  const entry = MODEL_IMAGES[model]
  return entry ? `${MODEL_IMAGE_BASE}/${entry.file}` : null
}

export function officialModelImageAttribution(model: string): ModelImageAttribution | null {
  const entry = MODEL_IMAGES[model]
  if (!entry) return null
  const { author, license, licenseUrl, sourceUrl } = entry
  return { author, license, licenseUrl, sourceUrl }
}
