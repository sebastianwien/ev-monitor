import axiosInstance from './axios';

export interface TripStatsSnapshot {
  distanceKm?: number;
  durationMinutes?: number;
  consumedKwh?: number;
  avgSpeedKmh?: number;
  maxSpeedKmh?: number;
  socStart?: number;
  socEnd?: number;
  outsideTempCelsius?: number;
  startedAt?: string;
  endedAt?: string;
}

export interface StoryBlock {
  type: 'text' | 'tripStats';
  markdown?: string;
  tripId?: string;
  label?: string;
  stats?: TripStatsSnapshot;
}

export interface TripStory {
  id: string;
  title: string;
  slug: string;
  summary: string | null;
  language: string;
  status: 'DRAFT' | 'PUBLISHED';
  blocks: StoryBlock[];
  createdAt: string;
  updatedAt: string;
  publishedAt: string | null;
}

export interface PublicTripStory {
  title: string;
  slug: string;
  summary: string | null;
  language: string;
  authorUsername: string;
  publishedAt: string | null;
  blocks: StoryBlock[];
}

export interface PublicTripStorySummary {
  title: string;
  slug: string;
  summary: string | null;
  language: string;
  authorUsername: string;
  publishedAt: string | null;
}

/** Client payload - trip widgets send only tripId + label, stats are snapshotted server-side. */
export interface SaveStoryRequest {
  title: string;
  summary: string | null;
  language: string;
  blocks: Array<{ type: string; markdown?: string; tripId?: string; label?: string }>;
}

export const storyService = {
  async getMyStories(): Promise<TripStory[]> {
    const response = await axiosInstance.get('/stories');
    return response.data;
  },

  async getStory(id: string): Promise<TripStory> {
    const response = await axiosInstance.get(`/stories/${id}`);
    return response.data;
  },

  async createStory(request: SaveStoryRequest): Promise<TripStory> {
    const response = await axiosInstance.post('/stories', request);
    return response.data;
  },

  async updateStory(id: string, request: SaveStoryRequest): Promise<TripStory> {
    const response = await axiosInstance.put(`/stories/${id}`, request);
    return response.data;
  },

  async publishStory(id: string): Promise<TripStory> {
    const response = await axiosInstance.post(`/stories/${id}/publish`);
    return response.data;
  },

  async unpublishStory(id: string): Promise<TripStory> {
    const response = await axiosInstance.post(`/stories/${id}/unpublish`);
    return response.data;
  },

  async deleteStory(id: string): Promise<void> {
    await axiosInstance.delete(`/stories/${id}`);
  },

  async getPublicStories(): Promise<PublicTripStorySummary[]> {
    const response = await axiosInstance.get('/public/stories');
    return response.data;
  },

  async getPublicStory(slug: string): Promise<PublicTripStory> {
    const response = await axiosInstance.get(`/public/stories/${slug}`);
    return response.data;
  },
};
