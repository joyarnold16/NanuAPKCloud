# Create: edit a photo from the device

Open **Create → Add image from device**, select a photo, then describe how the finished picture should look. A preview and a **Change strength** slider appear. The default is 45%; lower values preserve more of the source and higher values change more. Remove the photo to return to text-to-image generation. The existing image model download is sufficient; no cloud image API or new model download is introduced.

The system document picker grants access to the selected image. Nanu decodes it with Android ImageDecoder, applies its orientation, limits the longest imported edge to 2048 pixels, and saves a private PNG copy without original metadata. The original device photo is not modified. Supported formats depend on Android's decoder, including common JPEG, PNG, WebP and HEIC images. Invalid/missing files show an error. Import runs off the UI thread.

The Original aspect option uses the source proportions at the local engine's limited output size. Other aspect options center-crop rather than stretch the photo. Very narrow panoramas are constrained by the minimum 64-pixel dimension. Quality/size choices remain the existing conservative mobile settings; this is not a full-resolution photo editor.

The foreground service receives the saved private path, makes a temporary image at the exact generation dimensions, and passes `--init-img` plus `--strength` to the existing sd-cli. The temporary file is removed on success, failure or cancellation. The source copy is retained for reopening and retrying from history. Removing it from the composer does not invalidate an already-running task. Exported edited images are separate files, using the existing gallery-saving behavior.

History stores source references, prompt and edit options with the assistant reply. Regenerate/Edit from chat opens Create with those settings. A missing source is explicitly reported and must be replaced or removed before generation.

This is Stable Diffusion 1.5 image-to-image generation, not an instruction-tuned or masked photo editor. Describe the desired final scene. It can change composition, faces, text and fine details; exact identity preservation and isolated edits are not guaranteed. Selecting a photo does not enable face-locking, brushing or inpainting.

The command-line options were checked against the pinned engine revision:
https://github.com/leejet/stable-diffusion.cpp/blob/97d2990807fe6d558e395f8764198d7c7e7b411c/examples/common/common.cpp

Automated tests cover original-file preservation, PNG import, EXIF orientation, size limits, invalid/missing inputs, crop output, locale-independent engine arguments and persistence of edit settings. Real-device image generation and visual-quality checks remain necessary.
