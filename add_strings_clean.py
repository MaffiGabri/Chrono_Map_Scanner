import re

for file_path, xml_to_add in [
    ("android-app/app/src/main/res/values/strings.xml", "strings_to_add_it.xml"),
    ("android-app/app/src/main/res/values-en/strings.xml", "strings_to_add_en.xml")
]:
    with open(xml_to_add, "r") as f:
        to_add = f.read()

    with open(file_path, "r") as f:
        content = f.read()

    content = content.replace("</resources>", to_add + "\n</resources>")

    with open(file_path, "w") as f:
        f.write(content)
