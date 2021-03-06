/*
 * Copyright (c) 2014 Oculus Info Inc. http://www.oculusinfo.com/
 * 
 * Released under the MIT License.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oculusinfo.factory;


import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.*;



/**
 * This class provides a basis for factories that are configurable via JSON or
 * java property files.
 * 
 * This provides the standard glue of getting properties consistently in either
 * case, of documenting the properties needed by a factory, and, potentially, of
 * writing out a configuration.
 * 
 * @param <T> The type of object constructed by this factory.
 * 
 * @author nkronenfeld
 */
abstract public class ConfigurableFactory<T> {
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigurableFactory.class);



	private String                        _name;
	private Class<T>                      _factoryType;
	private List<String>                  _rootPath;
	private List<ConfigurableFactory<?>>  _children;
	private boolean                       _configured;
	private JSONObject                    _configurationNode;
	private Set<ConfigurationProperty<?>> _properties;
	private ConfigurableFactory<?>        _parent;



	/**
	 * Create a factory
	 * 
	 * @param factoryType The type of object to be constructed by this factory.
	 *            Can not be null.
	 * @param parent The parent factory; all configuration nodes of this factory
	 *            will be under the parent factory's root configuration node.
	 * @param path The path from the parent factory's root configuration node to
	 *            this factory's root configuration node.
	 */
	protected ConfigurableFactory (Class<T> factoryType, ConfigurableFactory<?> parent, List<String> path) {
		this(null, factoryType, parent, path);
	}
    
	/**
	 * Create a factory
	 * 
	 * @param name A name by which this factory can be known, to be used to
	 *            differentiate it from other child factories of this factory's
	 *            parent that return the same type.
	 * @param factoryType The type of object to be constructed by this factory.
	 *            Can not be null.
	 * @param parent The parent factory; all configuration nodes of this factory
	 *            will be under the parent factory's root configuration node.
	 * @param path The path from the parent factory's root configuration node to
	 *            this factory's root configuration node.
	 */
	protected ConfigurableFactory (String name, Class<T> factoryType, ConfigurableFactory<?> parent, List<String> path) {
		_name = name;
		_factoryType = factoryType;
		List<String> rootPath = new ArrayList<>();
		if (null != parent) {
			rootPath.addAll(parent.getRootPath());
		}
		if (null != path) {
			rootPath.addAll(path);
		}
		_rootPath = Collections.unmodifiableList(rootPath);
		_children = new ArrayList<>();
		_configured = false;
		_properties = new HashSet<>();
		
		//NOTE: this should not be set to the parent passed in cause the parent won't necessarily
		//be created before the children if you're doing a bottom up approach for some reason.
		_parent = null; 
	}

	/**
	 * Get the root node in the tree of configurables.
	 * @return Returns the root of the configurable factories, or this factory if no parent is set.
	 */
	public ConfigurableFactory<?> getRoot() {
		return (_parent != null)? _parent.getRoot() : this;
	}

	/**
	 * Get the root path for configuration information for this factory.
	 * 
	 * @return A list of strings describing the path to this factory's
	 *         configuration information. Guaranteed not to be null.
	 */
	public List<String> getRootPath () {
		return _rootPath;
	}
	
	/**
	 * Get the name associated with the factory.
	 * @return A string name if provided upon construction, or else null if none was provided.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * List out all properties directly expected by this factory.
	 */
	protected Iterable<ConfigurationProperty<?>> getProperties () {
		return _properties;
	}

	/**
	 * Add a property to the list of properties used by this factory
	 * 
	 * @param property
	 */
	protected <PT> void addProperty (ConfigurationProperty<PT> property) {
		_properties.add(property);
	}

	/**
	 * Indicates if an actual value is recorded for the given property.
	 * 
	 * @param property The property of interest.
	 * @return True if the property is listed and non-default in the factory.
	 */
	public boolean hasPropertyValue (ConfigurationProperty<?> property) {
		return (_configured && null != _configurationNode && _configurationNode.has(property.getName()));
	}

	/**
	 * Get the value read at configuration time for the given property.
	 * 
	 * The behavior of this function is undefined if called before
	 * readConfiguration (either version).
	 */
	public <PT> PT getPropertyValue (ConfigurationProperty<PT> property) {
		if (!hasPropertyValue(property))
			return property.getDefaultValue();

		try {
			return property.unencodeJSON(new JSONNode(_configurationNode, property.getName()));
		} catch (JSONException e) {
			// Must not have been there.  Ignore, leaving as default.
		} catch (ConfigurationException e) {
			// Error within configuration.
			// Use default, but also warn about it.
			LOGGER.warn("Error reading property {} from configuration {}", property, _configurationNode);
		}
		return property.getDefaultValue();
	}


	/**
	 * Add a child factory, to be used by this factory.
	 * 
	 * @param child The child to add.
	 */
	protected void addChildFactory (ConfigurableFactory<?> child) {
		_children.add(child);
		child._parent = this;
	}

	/**
	 * Create the object provided by this factory.
	 * @return The object 
	 */
	protected abstract T create ();

	/**
	 * Get one of the goods managed by this factory.
	 * 
	 * This version returns a new instance each time it is called.
	 * 
	 * @param goodsType The type of goods desired.
	 */
	public <GT> GT produce (Class<GT> goodsType) throws ConfigurationException {
		return produce(null, goodsType);
	}

	/**
	 * Get one of the goods managed by this factory.
	 * 
	 * This version returns a new instance each time it is called.
	 * 
	 * @param name The name of the factory from which to obtain the needed
	 *            goods. Null indicates that the factory name doesn't matter.
	 * @param goodsType The type of goods desired.
	 */
	public <GT> GT produce (String name, Class<GT> goodsType) throws ConfigurationException {
		if (!_configured) {
			throw new ConfigurationException("Attempt to get value from uninitialized factory");
		}

		if ((null == name || name.equals(_name)) && goodsType.equals(_factoryType)) {
			return goodsType.cast(create());
		} else {
			for (ConfigurableFactory<?> child: _children) {
				GT result = child.produce(name, goodsType);
				if (null != result) return result;
			}
		}
		return null;
	}

	public <GT> ConfigurableFactory<GT> getProducer (Class<GT> goodsType) {
		return getProducer(null, goodsType);
	}

	// We are suppressing the warnings in the
	//    return this;
	// line. We have just, in the line before, checked that goodsType - which is
	// Class<GT> - matches _factoryType - which is Class<T> - so therefore, GT
	// and T must be the same, so this cast is guaranteed safe, even if the
	// compiler can't figure that out.
	@SuppressWarnings({"unchecked", "rawtypes"})
	public <GT> ConfigurableFactory<GT> getProducer (String name, Class<GT> goodsType) {
		if ((null == name || name.equals(_name)) && goodsType.equals(_factoryType)) {
			return (ConfigurableFactory) this;
		} else {
			for (ConfigurableFactory<?> child: _children) {
				ConfigurableFactory<GT> result = child.getProducer(name, goodsType);
				if (null != result) return result;
			}
		}
		return null;
	}



	// /////////////////////////////////////////////////////////////////////////
	// Section: Reading from and writing to a JSON file
	//

	/**
	 * Initialize needed construction values from a properties list.
	 * 
	 * @param rootNode The root node of all configuration information for this
	 *            factory.
	 * @throws ConfigurationException If something goes wrong in configuration,
	 *             or if configuration is called twice.
	 */
	public void readConfiguration (JSONObject rootNode) throws ConfigurationException {
		if (_configured) {
			throw new ConfigurationException("Attempt to configure factory "+this+" twice");
		}

		try {
			_configurationNode = getConfigurationNode(rootNode);
			for (ConfigurableFactory<?> child: _children) {
				child.readConfiguration(rootNode);
			}
			_configured = true;
		} catch (JSONException e) {
			throw new ConfigurationException("Error configuring factory "+this.getClass().getName(), e);
		}
	}

	/*
	 * Gets the JSON node with all this factory's configuration information
	 * 
	 * @param rootNode The root JSON node containing all configuration
	 * information.
	 */
	private JSONObject getConfigurationNode (JSONObject rootNode) throws JSONException {
		return getLeafNode(rootNode, _rootPath);
	}

	/**
	 * Get the sub-node of a root node specified by a given path.
	 * 
	 * @param rootNode The root JSON object whose sub-node is desired.
	 * @param path A list of keys to follow from the root node to find the
	 *            desired leaf.
	 * @return The leaf node, or null if any branch along the path is missing.
	 */
	public static JSONObject getLeafNode (JSONObject rootNode, List<String> path) {
		JSONObject target = rootNode;
		for (String pathElt: path) {
			if (target.has(pathElt)) {
				try {
					target = target.getJSONObject(pathElt);
				} catch (JSONException e) {
					// Node is of the wrong type; default everything.
					target = null;
					break;
				}
			} else {
				target = null;
				break;
			}
		}
		return target;
	}



	public void writeConfigurationInformation (PrintStream stream) {
		writeConfigurationInformation(stream, "");
	}

	private <PT> void writePropertyValue (PrintStream stream, String prefix, ConfigurationProperty<PT> property) {
		if (hasPropertyValue(property)) {
			stream.println(prefix+property.getName()+": "+property.encode(property.getDefaultValue())+" (DEFAULT)");
		} else {
			PT value;
			try {
				value = property.unencodeJSON(new JSONNode(_configurationNode, property.getName()));
				stream.println(prefix+property.getName()+": "+property.encode(value));
			} catch (JSONException|ConfigurationException e) {
				stream.println(prefix+property.getName()+": "+property.encode(property.getDefaultValue())+" (DEFAULT - read error)");
			}
		}
	}

	private String mkString (List<?> list, String separator) {
		if (null == list) return "null";

		boolean first = true;
		String result = "";
		for (Object elt: list) {
			if (first) first = false;
			else result += separator;
			result = result + elt;
		}
		return result;
	}

	public void writeConfigurationInformation (PrintStream stream, String prefix) {
		stream.println(prefix+"Configuration for "+this.getClass().getSimpleName()+" (node name "+_name+", path: "+mkString(_rootPath, ", ")+"):");
		prefix = prefix + "  ";
		for (ConfigurationProperty<?> property: _properties) {
			writePropertyValue(stream, prefix, property);
		}
		stream.println();
		for (ConfigurableFactory<?> child: _children) {
			child.writeConfigurationInformation(stream, prefix);
		}
	}

	/**
	 * Get the JSON object used to configure this factory.
	 * 
	 * @return The configuring JSON object, or null if this factory has not yet
	 *         been configured.
	 */
	protected JSONObject getConfigurationNode () {
		return _configurationNode;
	}

	/**
	 * Gets the class of object produced by this factory.
	 */
	protected Class<? extends T> getFactoryType () {
		return _factoryType;
	}
}
