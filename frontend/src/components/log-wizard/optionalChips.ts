import type { Component } from 'vue'
import { SunIcon } from '@heroicons/vue/24/outline'
import type { LogFormData } from '../log-form/logFormData'
import CityIcon from '../icons/CityIcon.vue'
import RouteIcon from '../icons/RouteIcon.vue'
import RoadIcon from '../icons/RoadIcon.vue'
import WheelIcon from '../icons/WheelIcon.vue'
import SnowflakeIcon from '../icons/SnowflakeIcon.vue'

/** Strecke und Reifen: Übersetzungsschlüssel und Icon je Wert, geteilt von Details-Formular und Vorschau-Pillen. */
export const ROUTE_CHIPS: { value: LogFormData['routeType']; key: string; icon: Component }[] = [
  { value: 'CITY', key: 'logwizard.d_route_city', icon: CityIcon },
  { value: 'COMBINED', key: 'logwizard.d_route_mixed', icon: RouteIcon },
  { value: 'HIGHWAY', key: 'logwizard.d_route_highway', icon: RoadIcon },
]
export const TIRE_CHIPS: { value: LogFormData['tireType']; key: string; icon: Component }[] = [
  { value: 'SUMMER', key: 'logwizard.d_tire_summer', icon: SunIcon },
  { value: 'ALL_YEAR', key: 'logwizard.d_tire_allyear', icon: WheelIcon },
  { value: 'WINTER', key: 'logwizard.d_tire_winter', icon: SnowflakeIcon },
]
