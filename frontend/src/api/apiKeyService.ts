import axiosInstance from './axios';

export interface ApiKeyResponse {
  id: string;
  keyPrefix: string;
  name: string;
  lastUsedAt: string | null;
  createdAt: string;
  mergeSessions: boolean;
}

export interface ApiKeyCreatedResponse extends ApiKeyResponse {
  plaintextKey: string;
}

export const apiKeyService = {
  async listKeys(): Promise<ApiKeyResponse[]> {
    const response = await axiosInstance.get('/user/api-keys');
    return response.data;
  },

  async createKey(name: string): Promise<ApiKeyCreatedResponse> {
    const response = await axiosInstance.post('/user/api-keys', { name });
    return response.data;
  },

  async deleteKey(id: string): Promise<void> {
    await axiosInstance.delete(`/user/api-keys/${id}`);
  },

  async updateMergeSessions(id: string, mergeSessions: boolean): Promise<ApiKeyResponse> {
    const response = await axiosInstance.patch(`/user/api-keys/${id}/merge-sessions`, { mergeSessions });
    return response.data;
  },
};
