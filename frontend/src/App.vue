<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import SpritMonitorImport from './components/SpritMonitorImport.vue'
import LogFormModal from './components/LogFormModal.vue'
import FloatingActionButton from './components/FloatingActionButton.vue'
import { Bars3Icon, XMarkIcon, ChartBarIcon, TruckIcon, ArrowDownTrayIcon, UserIcon, ArrowRightOnRectangleIcon, Cog6ToothIcon } from '@heroicons/vue/24/outline'

const router = useRouter()
const authStore = useAuthStore()
const showImportOverlay = ref(false)
const showLogFormModal = ref(false)
const mobileMenuOpen = ref(false)

const handleLogout = () => {
  authStore.logout()
  mobileMenuOpen.value = false
}

const handleNewLog = () => {
  // Check if desktop (≥768px) or mobile
  const isDesktop = window.innerWidth >= 768

  if (isDesktop) {
    showLogFormModal.value = true
  } else {
    router.push('/erfassen')
  }
  mobileMenuOpen.value = false
}

const closeMobileMenu = () => {
  mobileMenuOpen.value = false
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex flex-col">
    <!-- Navigation -->
    <nav class="bg-indigo-600 shadow-md text-white" v-if="authStore.isAuthenticated()">
      <div class="px-4 py-3">
        <div class="flex justify-between items-center">
          <!-- Left: Logo + Nav Buttons (Desktop) -->
          <div class="flex items-center space-x-4">
            <div class="text-xl font-bold tracking-wide">EV Monitor</div>

            <div class="hidden md:flex items-center space-x-4">
              <button
                @click="handleNewLog"
                class="px-3 py-2 rounded-md text-sm font-medium bg-green-600 hover:bg-green-700 transition">
                Ladevorgang erfassen
              </button>
              <router-link
                to="/statistics"
                class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
                :class="{ 'bg-indigo-700': $route.path === '/statistics' }">
                Statistiken
              </router-link>
              <router-link
                to="/cars"
                class="px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-500 transition"
                :class="{ 'bg-indigo-700': $route.path === '/cars' }">
                Fahrzeuge
              </router-link>
              <button
                @click="showImportOverlay = true"
                class="flex items-center gap-2 px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm">
                <ArrowDownTrayIcon class="h-5 w-5" />
                <span class="hidden lg:inline">Sprit-Monitor Import</span>
                <span class="lg:hidden">Import</span>
              </button>
            </div>
          </div>

          <!-- Right: User Info + Logout (Desktop) / Hamburger (Mobile) -->
          <div class="hidden md:flex items-center space-x-4">
            <router-link
              v-if="authStore.user"
              to="/settings"
              class="flex items-center gap-2 px-3 py-1.5 text-sm border border-indigo-400 rounded-md bg-indigo-500 bg-opacity-30 hover:bg-opacity-50 transition">
              <UserIcon class="h-4 w-4" />
              <span>{{ authStore.user.sub }}</span>
            </router-link>
            <button @click="handleLogout" class="px-4 py-2 bg-indigo-700 hover:bg-indigo-800 rounded-lg text-sm font-medium transition shadow-sm">
              Abmelden
            </button>
          </div>

          <!-- Mobile Hamburger Button -->
          <button
            @click="mobileMenuOpen = !mobileMenuOpen"
            class="md:hidden p-2 rounded-md hover:bg-indigo-700 transition"
            aria-label="Menu">
            <Bars3Icon v-if="!mobileMenuOpen" class="h-6 w-6" />
            <XMarkIcon v-else class="h-6 w-6" />
          </button>
        </div>
      </div>

      <!-- Mobile Menu -->
      <div
        v-if="mobileMenuOpen"
        class="md:hidden border-t border-indigo-500 bg-indigo-700">
        <div class="px-4 py-3 space-y-2">
          <router-link
            to="/statistics"
            @click="closeMobileMenu"
            class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
            :class="{ 'bg-indigo-800': $route.path === '/statistics' }">
            <ChartBarIcon class="h-5 w-5" />
            <span>Statistiken</span>
          </router-link>
          <router-link
            to="/cars"
            @click="closeMobileMenu"
            class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
            :class="{ 'bg-indigo-800': $route.path === '/cars' }">
            <TruckIcon class="h-5 w-5" />
            <span>Fahrzeuge</span>
          </router-link>
          <router-link
            to="/settings"
            @click="closeMobileMenu"
            class="flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition"
            :class="{ 'bg-indigo-800': $route.path === '/settings' }">
            <Cog6ToothIcon class="h-5 w-5" />
            <span>Einstellungen</span>
          </router-link>
          <button
            @click="showImportOverlay = true; mobileMenuOpen = false"
            class="w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition">
            <ArrowDownTrayIcon class="h-5 w-5" />
            <span>Sprit-Monitor Import</span>
          </button>
          <div v-if="authStore.user" class="flex items-center gap-2 px-3 py-2 text-sm text-indigo-200">
            <UserIcon class="h-5 w-5" />
            <span>{{ authStore.user.sub }}</span>
          </div>
          <button
            @click="handleLogout"
            class="w-full flex items-center gap-2 px-3 py-2 rounded-md text-sm font-medium hover:bg-indigo-600 transition text-red-300">
            <ArrowRightOnRectangleIcon class="h-5 w-5" />
            <span>Abmelden</span>
          </button>
        </div>
      </div>
    </nav>
    <main :class="{ 'md:py-10 md:px-4': authStore.isAuthenticated() }">
      <router-view></router-view>
    </main>

    <!-- Footer (only for authenticated users) -->
    <footer v-if="authStore.isAuthenticated()" class="bg-gray-50 border-t border-gray-200 py-6 mt-auto">
      <div class="container mx-auto px-4">
        <div class="text-center text-sm text-gray-600 space-y-3">
          <p>
            © 2026 EV Monitor ·
            <router-link to="/datenschutz" class="hover:text-green-600 underline">Datenschutz</router-link> ·
            <router-link to="/impressum" class="hover:text-green-600 underline">Impressum</router-link> ·
            <router-link to="/agb" class="hover:text-green-600 underline">AGB</router-link>
          </p>
          <p class="text-xs text-gray-500 text-center">
            Datenimport powered by
            <a
              href="https://www.spritmonitor.de"
              target="_blank"
              rel="noopener noreferrer"
              class="text-green-600 hover:text-green-700 hover:underline font-medium">
              Sprit-Monitor API
            </a>
          </p>
        </div>
      </div>
    </footer>

    <!-- Sprit-Monitor Import Overlay -->
    <SpritMonitorImport v-if="showImportOverlay" @close="showImportOverlay = false" />

    <!-- Floating Action Button (only when authenticated) -->
    <FloatingActionButton v-if="authStore.isAuthenticated()" @click="handleNewLog" />

    <!-- Log Form Modal (Desktop only) -->
    <LogFormModal v-if="showLogFormModal && authStore.isAuthenticated()" @close="showLogFormModal = false" />
  </div>
</template>
