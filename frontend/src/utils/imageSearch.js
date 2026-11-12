import { reactive } from 'vue'

// the picture picked in the header's search box, handed to the "Tim bang hinh anh" page
export const imageSearchState = reactive({ file: null, previewUrl: null })

export function pickImage(file) {
  if (imageSearchState.previewUrl) URL.revokeObjectURL(imageSearchState.previewUrl)
  imageSearchState.file = file
  imageSearchState.previewUrl = file ? URL.createObjectURL(file) : null
}
