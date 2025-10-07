from flask import Flask, request, jsonify
from flask_cors import CORS
from transformers import pipeline
from PIL import Image
import io
import base64
import numpy as np
import cv2
import logging
import os
import traceback

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)  # Enable CORS for all routes


segmenter = None


def initialize_segmenter():
    """Load the segmentation model"""
    global segmenter
    try:
        logger.info("Loading segmentation model...")
        segmenter = pipeline("image-segmentation",
                             model="mattmdjaga/segformer_b2_clothes")
        logger.info("Model loaded successfully!")
        return True
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        return False


@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    status = "ready" if segmenter is not None else "initializing"
    return jsonify({
        "status": status,
        "message": "Clothing segmentation server is running"
    })


@app.route('/segment', methods=['POST'])
def segment_image():
    """Main segmentation endpoint - FIXED VERSION"""
    try:
        if segmenter is None:
            return jsonify({"success": False, "error": "Model not initialized"}), 500

        # Get image from request
        data = request.get_json()
        if not data or 'image' not in data:
            return jsonify({"success": False, "error": "No image data provided"}), 400

        logger.info("Processing image...")

        # Decode base64 image
        image_data = base64.b64decode(data['image'].split(',')[1])
        image = Image.open(io.BytesIO(image_data)).convert('RGB')
        original_size = image.size

        logger.info(f"Image loaded: {original_size}")

        # Run segmentation
        results = segmenter(image)

        # Log all results for debugging
        logger.info("=== ALL SEGMENTATION RESULTS ===")
        for i, result in enumerate(results):
            label = result['label']
            score = result.get('score', 0) or 0
            logger.info(f"Result {i}: {label} (score: {score:.3f})")
        logger.info("=== END RESULTS ===")

        # Process results to find clothing items
        detected_items = []
        best_result = None
        best_confidence = 0

        for result in results:
            label = result['label'].lower()
            mask = result.get('mask')

            # Skip if no mask
            if mask is None:
                continue

            # Calculate area ratio of the mask
            mask_np = np.array(mask)
            total_pixels = mask_np.size
            clothing_pixels = np.sum(mask_np > 0)
            area_ratio = clothing_pixels / total_pixels if total_pixels > 0 else 0

            # Use area ratio as confidence score
            confidence = float(area_ratio)

            # Clothing detection criteria
            clothing_labels = ['upper-clothes', 'shirt', 'dress', 'pants', 'skirt',
                               'coat', 'jacket', 'top', 't-shirt', 'hoodie']
            background_labels = ['background',
                                 'wall', 'floor', 'ground', 'sky']

            is_clothing = any(
                cloth_label in label for cloth_label in clothing_labels)
            is_background = any(
                bg_label in label for bg_label in background_labels)

            # Accept if it's clothing OR has significant area and isn't background
            if is_clothing or (confidence > 0.01 and not is_background):
                detected_items.append({
                    'label': result['label'],
                    'score': confidence  # Use area-based confidence
                })

                if confidence > best_confidence:
                    best_confidence = confidence
                    best_result = result

        logger.info(f"Final detected items: {len(detected_items)}")

        # Create segmented image if we have a best result
        segmented_image = None
        if best_result:
            segmented_image = create_better_segmentation(image, best_result)

        # Prepare response
        buffered = io.BytesIO()
        if segmented_image:
            segmented_image.save(buffered, format="PNG", optimize=True)
        else:
            # Fallback: return original image
            image.save(buffered, format="PNG")

        img_str = base64.b64encode(buffered.getvalue()).decode()

        response_data = {
            "success": True,
            "detected_items": detected_items,
            "primary_item": best_result['label'] if best_result else "none",
            "confidence": best_confidence,
            "segmented_image": f"data:image/png;base64,{img_str}",
            "image_size": original_size,
            "message": f"Found {len(detected_items)} items" if detected_items else "No clothing detected"
        }

        logger.info("Processing completed successfully")
        return jsonify(response_data)

    except Exception as e:
        logger.error(f"Error processing image: {e}")
        logger.error(traceback.format_exc())
        return jsonify({"success": False, "error": str(e)}), 500


def create_better_segmentation(original_image, segmentation_result):
    """Use the same method as your working test code"""
    try:
        mask = segmentation_result.get('mask')
        if mask is None:
            return None

        # Convert to numpy arrays
        img_np = np.array(original_image.convert("RGBA"))
        mask_np = np.array(mask)

        # Create alpha channel using the same logic as your test code
        alpha = np.zeros_like(mask_np, dtype=np.uint8)

        alpha[mask_np > 128] = 255

        # Apply Gaussian blur for smooth edges
        alpha_blur = cv2.GaussianBlur(alpha.astype(np.float32), (7, 7), 2)
        alpha_smooth = np.clip(alpha_blur, 0, 255).astype(np.uint8)

        # Create RGBA image
        rgba = np.zeros((img_np.shape[0], img_np.shape[1], 4), dtype=np.uint8)
        rgba[:, :, :3] = img_np[:, :, :3]  # Copy RGB channels
        rgba[:, :, 3] = alpha_smooth  # Apply alpha channel

        return Image.fromarray(rgba, 'RGBA')

    except Exception as e:
        logger.error(f"Error in create_better_segmentation: {e}")
        return None


if __name__ == '__main__':
    # Initialize the model when server starts
    if initialize_segmenter():
        # Get port from environment variable or default to 5000
        port = int(os.environ.get('PORT', 5000))

        # Run the server
        logger.info(f"Starting Flask server on port {port}")
        logger.info("Make sure your Android app uses the correct IP address")
        logger.info("For local testing, use: http://localhost:5000")
        logger.info(
            "For device testing, use your machine's local network IP address")
        app.run(host='0.0.0.0', port=port, debug=True, threaded=True)
    else:
        logger.error("Failed to initialize model. Server cannot start.")
