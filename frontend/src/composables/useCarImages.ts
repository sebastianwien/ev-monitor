import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { carService, type Car } from '../api/carService'
import { useCarStore } from '../stores/car'
import { useCoinStore } from '../stores/coins'
import type { Ref } from 'vue'

export function useCarImages(
  cars: Ref<Car[]>,
  error: Ref<string | null>,
  showToast: Ref<boolean>,
  toastMessage: Ref<string>,
) {
  const { t } = useI18n()
  const carStore = useCarStore()
  const coinStore = useCoinStore()

  const imageBlobUrls = ref<Record<string, string>>({})
  const imageUploading = ref<Record<string, boolean>>({})
  const imagePublicForUpload = ref<Record<string, boolean>>({})
  const visibilityTimers: Record<string, ReturnType<typeof setTimeout>> = {}

  const revokeAllBlobUrls = () => {
    Object.values(imageBlobUrls.value).forEach(url => URL.revokeObjectURL(url))
    imageBlobUrls.value = {}
  }

  const loadCarImages = async (carList: Car[]) => {
    const carsWithImages = carList.filter(c => c.imageUrl)
    for (const car of carsWithImages) {
      try {
        const blobUrl = await carService.getCarImageBlobUrl(car.id)
        imageBlobUrls.value = { ...imageBlobUrls.value, [car.id]: blobUrl }
      } catch {
        // Image might not be accessible - ignore silently
      }
    }
  }

  const initVisibility = (carList: Car[]) => {
    const visibility: Record<string, boolean> = {}
    carList.forEach(c => { visibility[c.id] = c.imagePublic })
    imagePublicForUpload.value = visibility
  }

  const handleVisibilityChange = (carId: string, isPublic: boolean) => {
    imagePublicForUpload.value = { ...imagePublicForUpload.value, [carId]: isPublic }
    const car = cars.value.find(c => c.id === carId)
    if (!car?.imageUrl) return

    clearTimeout(visibilityTimers[carId])
    visibilityTimers[carId] = setTimeout(async () => {
      try {
        const result = await carService.updateCarImageVisibility(carId, isPublic)
        cars.value = cars.value.map(c => c.id === carId ? result.car : c)
        carStore.invalidateCars()
        if (result.coinsAwarded > 0) {
          coinStore.refresh()
          toastMessage.value = t('cars.toast_image_public', { n: result.coinsAwarded })
          showToast.value = true
          setTimeout(() => { showToast.value = false }, 5000)
        }
      } catch (err: any) {
        error.value = err.response?.data?.message || t('cars.error_visibility')
      }
    }, 500)
  }

  const handleImageUpload = async (carId: string, event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return

    imageUploading.value = { ...imageUploading.value, [carId]: true }
    try {
      error.value = null
      const isPublic = imagePublicForUpload.value[carId] ?? false
      const result = await carService.uploadCarImage(carId, file, isPublic)
      cars.value = cars.value.map(c => c.id === carId ? result.car : c)
      carStore.invalidateCars()
      if (result.coinsAwarded > 0) {
        coinStore.refresh()
        toastMessage.value = t('cars.toast_upload', { n: result.coinsAwarded })
        showToast.value = true
        setTimeout(() => { showToast.value = false }, 5000)
      }
      if (imageBlobUrls.value[carId]) URL.revokeObjectURL(imageBlobUrls.value[carId])
      const blobUrl = await carService.getCarImageBlobUrl(carId)
      imageBlobUrls.value = { ...imageBlobUrls.value, [carId]: blobUrl }
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_upload')
    } finally {
      imageUploading.value = { ...imageUploading.value, [carId]: false }
      input.value = ''
    }
  }

  const handleDeleteImage = async (carId: string) => {
    if (!confirm(t('cars.confirm_delete_image'))) return
    try {
      error.value = null
      await carService.deleteCarImage(carId)
      if (imageBlobUrls.value[carId]) URL.revokeObjectURL(imageBlobUrls.value[carId])
      const { [carId]: _, ...rest } = imageBlobUrls.value
      imageBlobUrls.value = rest
      cars.value = cars.value.map(c => c.id === carId ? { ...c, imageUrl: null, imagePublic: false } : c)
      carStore.invalidateCars()
    } catch (err: any) {
      error.value = err.response?.data?.message || t('cars.error_delete_image')
    }
  }

  onUnmounted(() => {
    revokeAllBlobUrls()
    Object.values(visibilityTimers).forEach(clearTimeout)
  })

  return {
    imageBlobUrls, imageUploading, imagePublicForUpload,
    revokeAllBlobUrls, loadCarImages, initVisibility,
    handleVisibilityChange, handleImageUpload, handleDeleteImage,
  }
}
