import type { InjectionKey, Ref } from 'vue'

/**
 * Liegt das Gerüst WorkspaceFrame mit seinem Kopf über dem Inhalt? Dann blendet sich die eigene PublicNav der
 * Seite (Modellseiten) aus, nur der Kopf des Gerüsts (PublicNav home) bleibt. Default: nein.
 */
export const WORKSPACE_FRAME_HEAD: InjectionKey<Readonly<Ref<boolean>>> = Symbol('workspace-frame-head')
