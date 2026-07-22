import SwiftUI

struct ExamplesView: View {
    let onSelect: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(examplesByCategory(), id: \.0) { category, examples in
                    Section(header: Text(category.rawValue)) {
                        ForEach(examples) { example in
                            Button {
                                onSelect(example.code)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(example.title)
                                        .font(.subheadline.weight(.medium))
                                        .foregroundColor(.primary)
                                    Text(example.description)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Examples")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
