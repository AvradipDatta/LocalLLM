import os

def print_tree(start_path, indent="", file=None):
    for item in os.listdir(start_path):
        path = os.path.join(start_path, item)
        if os.path.isdir(path):
            file.write(f"{indent}|-- {item}/\n")
            print_tree(path, indent + "   ", file)
        else:
            file.write(f"{indent}|-- {item}\n")

# Change this path to your target folder
root_dir = root_dir = "D:\\Study\\My Project\\LocalLLM\\App\\src\\main"

output_file = "folder_structure.txt"

with open(output_file, "w", encoding="utf-8") as f:
    f.write(f"{os.path.basename(root_dir)}/\n")
    print_tree(root_dir, file=f)

print(f"✅ Folder structure saved to '{output_file}'")
