import axios from 'axios';
import { useAuthStore } from '../stores/auth';

const api = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    headers: {
        'Content-Type': 'application/json'
    }
});

api.interceptors.request.use(
    (config) => {
        const authStore = useAuthStore();
        if (authStore.token) {
            config.headers.Authorization = `Bearer ${authStore.token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

api.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.response && error.response.status === 401) {
            const authStore = useAuthStore();
            authStore.logout();
        }
        if (error.response?.status === 403 && error.response?.data?.error === 'DEMO_ACCOUNT_READONLY') {
            window.dispatchEvent(new CustomEvent('demo-account-blocked'));
        }
        return Promise.reject(error);
    }
);

export default api;
