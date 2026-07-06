## 2024-07-06 - FileProvider Path Exposure
**Vulnerability:** The application's `FileProvider` configuration in `res/xml/file_paths.xml` exposed the entire internal cache directory by using `<cache-path name="camera_tmp" path="." />`.
**Learning:** Exposing root directories like `.` via `FileProvider` allows any application granted URI access to potentially navigate and read other files within that root, violating the principle of least privilege.
**Prevention:** Always restrict `FileProvider` paths to explicitly scoped subdirectories (e.g., `camera_images/`) and ensure application logic writes shared files into those restricted directories.
